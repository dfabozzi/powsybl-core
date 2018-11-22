/**
 * Copyright (c) 2017-2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.cgmes.conversion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.powsybl.cgmes.conversion.elements.ACLineSegmentConversion;
import com.powsybl.cgmes.conversion.elements.AbstractConductingEquipmentConversion;
import com.powsybl.cgmes.conversion.elements.AbstractObjectConversion;
import com.powsybl.cgmes.conversion.elements.AcDcConverterConversion;
import com.powsybl.cgmes.conversion.elements.AsynchronousMachineConversion;
import com.powsybl.cgmes.conversion.elements.BusbarSectionConversion;
import com.powsybl.cgmes.conversion.elements.DcLineSegmentConversion;
import com.powsybl.cgmes.conversion.elements.EnergyConsumerConversion;
import com.powsybl.cgmes.conversion.elements.EnergySourceConversion;
import com.powsybl.cgmes.conversion.elements.EquivalentBranchConversion;
import com.powsybl.cgmes.conversion.elements.EquivalentInjectionConversion;
import com.powsybl.cgmes.conversion.elements.ExternalNetworkInjectionConversion;
import com.powsybl.cgmes.conversion.elements.NodeConversion;
import com.powsybl.cgmes.conversion.elements.PhaseTapChangerConversion;
import com.powsybl.cgmes.conversion.elements.RatioTapChangerConversion;
import com.powsybl.cgmes.conversion.elements.SeriesCompensatorConversion;
import com.powsybl.cgmes.conversion.elements.ShuntConversion;
import com.powsybl.cgmes.conversion.elements.StaticVarCompensatorConversion;
import com.powsybl.cgmes.conversion.elements.SubstationConversion;
import com.powsybl.cgmes.conversion.elements.SwitchConversion;
import com.powsybl.cgmes.conversion.elements.SynchronousMachineConversion;
import com.powsybl.cgmes.conversion.elements.TerminalLimitConversion;
import com.powsybl.cgmes.conversion.elements.ThreeWindingsTransformerConversion;
import com.powsybl.cgmes.conversion.elements.TwoWindingsTransformerConversion;
import com.powsybl.cgmes.conversion.elements.VoltageLevelConversion;
import com.powsybl.cgmes.model.CgmesModel;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.triplestore.api.PropertyBag;
import com.powsybl.triplestore.api.PropertyBags;

/**
 * @author Luma Zamarreño <zamarrenolm at aia.es>
 */
public class Conversion {

    public Conversion(CgmesModel cgmes) {
        this(cgmes, new Config());
    }

    public Conversion(CgmesModel cgmes, Conversion.Config config) {
        this.cgmes = cgmes;

        String networkId = cgmes.modelId();
        String sourceFormat = "CGMES";
        network = NetworkFactory.create(networkId, sourceFormat);
        context = new Context(cgmes, network, config);
    }

    public void report(Consumer<String> out) {
        new ReportTapChangers(cgmes, out).report();
    }

    public Network convertedNetwork() {
        if (LOG.isDebugEnabled() && cgmes.baseVoltages() != null) {
            LOG.debug(cgmes.baseVoltages().tabulate());
        }
        context.initialize();
        network.getProperties().put(NETWORK_PS_CGMES_MODEL_DETAIL,
                context.nodeBreaker()
                        ? NETWORK_PS_CGMES_MODEL_DETAIL_NODE_BREAKER
                        : NETWORK_PS_CGMES_MODEL_DETAIL_BUS_BRANCH);

        DateTime modelScenarioTime = cgmes.scenarioTime();
        DateTime modelCreated = cgmes.created();
        long forecastDistance = new Duration(modelCreated, modelScenarioTime).getStandardMinutes();
        network.setForecastDistance(forecastDistance >= 0 ? (int) forecastDistance : 0);
        network.setCaseDate(modelScenarioTime);
        LOG.info("cgmes scenarioTime       : {}", modelScenarioTime);
        LOG.info("cgmes modelCreated       : {}", modelCreated);
        LOG.info("network caseDate         : {}", network.getCaseDate());
        LOG.info("network forecastDistance : {}", network.getForecastDistance());
        Function<PropertyBag, AbstractObjectConversion> convf;

        convert(cgmes.substations(), s -> new SubstationConversion(s, context));
        convert(cgmes.voltageLevels(), vl -> new VoltageLevelConversion(vl, context));
        PropertyBags nodes = context.nodeBreaker()
                ? cgmes.connectivityNodes()
                : cgmes.topologicalNodes();
        String nodeTypeName = context.nodeBreaker()
                ? "ConnectivityNode"
                : "TopologicalNode";
        convert(nodes, n -> new NodeConversion(nodeTypeName, n, context));

        if (!context.config().createBusbarSectionForEveryConnectivityNode()) {
            convert(cgmes.busBarSections(), bbs -> new BusbarSectionConversion(bbs, context));
        }
        convert(cgmes.energyConsumers(), ec -> new EnergyConsumerConversion(ec, context));
        convert(cgmes.energySources(), es -> new EnergySourceConversion(es, context));

        convf = eqi -> new EquivalentInjectionConversion(eqi, context);
        convert(cgmes.equivalentInjections(), convf);

        convf = eni -> new ExternalNetworkInjectionConversion(eni, context);
        convert(cgmes.externalNetworkInjections(), convf);

        convert(cgmes.shuntCompensators(), sh -> new ShuntConversion(sh, context));

        convf = svc -> new StaticVarCompensatorConversion(svc, context);
        convert(cgmes.staticVarCompensators(), convf);
        convert(cgmes.switches(), sw -> new SwitchConversion(sw, context));

        convertACLineSegmentsToLines();
        convert(cgmes.equivalentBranches(), eqb -> new EquivalentBranchConversion(eqb, context));
        convert(cgmes.seriesCompensators(), sc -> new SeriesCompensatorConversion(sc, context));
        convertTransformers();
        convert(cgmes.ratioTapChangers(), rtc -> new RatioTapChangerConversion(rtc, context));
        convert(cgmes.phaseTapChangers(), ptc -> new PhaseTapChangerConversion(ptc, context));

        convf = asm -> new AsynchronousMachineConversion(asm, context);
        convert(cgmes.asynchronousMachines(), convf);

        // In CIM1 synchronous machines are added AFTER transmission lines and
        // transformers
        // Is there a strong reason to wait for these equipment to be added to the
        // network ?
        convert(cgmes.synchronousMachines(), sm -> new SynchronousMachineConversion(sm, context));

        // DC
        // Converters must be converted first
        convert(cgmes.acDcConverters(), c -> new AcDcConverterConversion(c, context));
        convert(cgmes.dcLineSegments(), l -> new DcLineSegmentConversion(l, context));

        convert(cgmes.terminalLimits(), l -> new TerminalLimitConversion(l, context));

        voltageAngles(nodes);
        checkShuntCompensators();
        debugTopo();

        return network;
    }

    public CgmesModel cgmes() {
        return cgmes;
    }

    public Network network() {
        return network;
    }

    public Conversion.Context context() {
        return context;
    }

    private void convert(
            PropertyBags elements,
            Function<PropertyBag, AbstractObjectConversion> f) {
        for (PropertyBag element : elements) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(element.tabulateLocals());
            }
            AbstractObjectConversion c = f.apply(element);
            if (c.insideBoundary()) {
                c.convertInsideBoundary();
            } else if (c.valid()) {
                c.convert();
            }
        }
    }

    private void convertACLineSegmentsToLines() {
        PropertyBags lines = cgmes.acLineSegments();

        // Context stores some statistics about line conversion
        context.startLinesConversion();

        // We will delay the conversion of some lines that have an end point on boundary
        // They have to be processed after all lines have been reviewed
        // (in fact we should review after all potential elements that could be present
        // in the model boundary have been put there, not only lines)
        Set<String> delayedBoundaryNodes = new HashSet<>();

        Iterator<PropertyBag> k = lines.stream().iterator();
        while (k.hasNext()) {
            PropertyBag line = k.next();
            if (LOG.isDebugEnabled()) {
                LOG.debug(line.tabulateLocals("ACLineSegment"));
            }
            ACLineSegmentConversion c = new ACLineSegmentConversion(line, context);
            context.anotherLineConversion(c);
            if (c.valid()) {
                String node = c.boundaryNode();
                if (node != null) {
                    context.boundary().addLineAtNode(line, node);
                    delayedBoundaryNodes.add(node);
                } else {
                    c.convert();
                }
            }
        }
        delayedBoundaryNodes.forEach(node -> {
            // At least each delayed boundary node should have one line
            PropertyBag line = context.boundary.linesAtNode(node).get(0);
            if (LOG.isDebugEnabled()) {
                LOG.debug(line.tabulateLocals("Line"));
            }
            ACLineSegmentConversion c = new ACLineSegmentConversion(line, context);
            c.convert();
        });
        context.endLinesConversion();
    }

    private void convertTransformers() {
        cgmes.groupedTransformerEnds().entrySet()
                .forEach(tends -> {
                    String t = tends.getKey();
                    PropertyBags ends = tends.getValue();
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Transformer {}, {}-winding", t, ends.size());
                        ends.forEach(e -> LOG.debug(e.tabulateLocals("TransformerEnd")));
                    }
                    AbstractConductingEquipmentConversion c = null;
                    if (ends.size() == 2) {
                        c = new TwoWindingsTransformerConversion(ends, context);
                    } else if (ends.size() == 3) {
                        c = new ThreeWindingsTransformerConversion(ends, context);
                    } else {
                        String what = String.format("PowerTransformer %s", t);
                        String reason = String.format("Has %d ends. Only 2 or 3 ends are supported",
                                ends.size());
                        context.invalid(what, reason);
                    }
                    if (c != null && c.valid()) {
                        c.convert();
                    }
                });
    }

    private void voltageAngles(PropertyBags nodes) {
        if (context.nodeBreaker()) {
            // FIXME(Luma): we create again one conversion object for every node
            // In node-breaker conversion,
            // set (voltage, angle) values after all nodes have been created and connected
            for (PropertyBag n : nodes) {
                NodeConversion nc = new NodeConversion("ConnectivityNode", n, context);
                if (!nc.insideBoundary()) {
                    nc.setVoltageAngleNodeBreaker();
                }
            }
        }
    }

    private void checkShuntCompensators() {
        // FIXME(Luma): remove this method before pull request,
        // it is here only for debugging purposes
        context.network().getShuntCompensators().forEach(shunt -> {
            Bus bus = shunt.getTerminal().getBusView().getBus();
            if (bus != null) {
                double v = bus.getV();
                if (Double.isNaN(v)) {
                    LOG.info("checkSC voltage NaN {}", bus.getId());
                } else {
                    double q = shunt.getTerminal().getQ();
                    double q1 = -1 * (v * v * shunt.getbPerSection() * shunt.getCurrentSectionCount());
                    double dq = Math.abs(q - q1);
                    LOG.info("checkSC Bus {} {}", bus.getId(), v);
                    LOG.info("checkSC {} {} {} {}", shunt.getId(), String.format("%.5f", dq), q, q1);
                }
            }
        });
    }
    
    private void debugTopo() {
        context.network().getVoltageLevels().forEach(vl -> {
            String name = vl.getSubstation().getName() + "-" + vl.getName();
            name = name.replace('/', '-');
            String filename = "/Users/zamarrenolm/temp/kkcgmes-" + name + ".dot";
            try {
                vl.exportTopology(filename);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }


    public static class Config {
        public List<String> substationIdsExcludedFromMapping() {
            return Collections.emptyList();
        }

        public boolean allowUnsupportedTapChangers() {
            return true;
        }

        public boolean useNodeBreaker() {
            return true;
        }

        public double lowImpedanceLineR() {
            return lowImpedanceLineR;
        }

        public double lowImpedanceLineX() {
            return lowImpedanceLineX;
        }

        public boolean convertBoundary() {
            return convertBoundary;
        }

        public void setConvertBoundary(boolean convertBoundary) {
            this.convertBoundary = convertBoundary;
        }

        public boolean mergeLinesUsingQuadripole() {
            return true;
        }

        public boolean changeSignForShuntReactivePowerFlowInitialState() {
            return changeSignForShuntReactivePowerFlowInitialState;
        }

        public void setChangeSignForShuntReactivePowerFlowInitialState(boolean b) {
            changeSignForShuntReactivePowerFlowInitialState = b;
        }

        public boolean computeFlowsAtBoundaryDanglingLines() {
            return true;
        }

        public boolean createBusbarSectionForEveryConnectivityNode() {
            return xxxCreateBusbarSectionForEveryConnectivityNode;
        }

        public void xxxSetCreateBusbarSectionForEveryConnectivityNode(boolean b) {
            xxxCreateBusbarSectionForEveryConnectivityNode = b;
        }

        private boolean convertBoundary = false;
        private boolean changeSignForShuntReactivePowerFlowInitialState;
        private double lowImpedanceLineR = 0.05;
        private double lowImpedanceLineX = 0.05;

        private boolean xxxCreateBusbarSectionForEveryConnectivityNode = false;
    }

    public static class Context {
        public Context(CgmesModel cgmes, Network network, Config config) {
            this.cgmes = cgmes;
            this.network = network;
            this.config = config;

            // Even if the CGMES model is node-breaker,
            // we could decide to ignore the connectivity nodes and
            // create buses directly from topological nodes,
            // the configuration says if we are performing the conversion
            // based on existing node-breaker info
            nodeBreaker = cgmes.isNodeBreaker() && config.useNodeBreaker();

            namingStrategy = new NamingStrategy.Identity();
            boundary = new Boundary(cgmes);
            substationIdMapping = new SubstationIdMapping(this);
            terminalMapping = new TerminalMapping();
            tapChangerTransformers = new TapChangerTransformers();
            dcMapping = new DcMapping(this);
            nodeMapping = new NodeMapping();
        }

        public CgmesModel cgmes() {
            return cgmes;
        }

        public Network network() {
            return network;
        }

        public Config config() {
            return config;
        }

        public boolean nodeBreaker() {
            return nodeBreaker;
        }

        public void initialize() {
            substationIdMapping.build();
            dcMapping.initialize();
        }

        public NamingStrategy namingStrategy() {
            return namingStrategy;
        }

        public TerminalMapping terminalMapping() {
            return terminalMapping;
        }

        public NodeMapping nodeMapping() {
            return nodeMapping;
        }

        public TapChangerTransformers tapChangerTransformers() {
            return tapChangerTransformers;
        }

        public SubstationIdMapping substationIdMapping() {
            return substationIdMapping;
        }

        public String substationNameEqContainer(PropertyBag p) {
            String eqcId = p.getId("EquipmentContainer");
            if (eqcId == null) {
                return null;
            }
            Substation substation = network.getSubstation(substationIdMapping().iidm(eqcId));
            if (substation != null) {
                return substation.getName();
            }
            return eqcId;
        }

        public Boundary boundary() {
            return boundary;
        }

        public DcMapping dc() {
            return dcMapping;
        }

        public String boundaryVoltageLevelId(String nodeId) {
            return nodeId + "_VL";
        }

        public String boundarySubstationId(String nodeId) {
            return nodeId + "_S";
        }

        public VoltageLevel createSubstationVoltageLevel(String nodeId, double nominalV) {
            String substationId = boundarySubstationId(nodeId);
            String vlId = boundaryVoltageLevelId(nodeId);
            String substationName = "boundary";
            String vlName = "boundary";
            return network()
                    .newSubstation()
                    .setId(namingStrategy().getId("Substation", substationId))
                    .setName(substationName)
                    // A non-null country code must be set
                    // This is an arbitrary country code, Bangladesh code BD also matches with
                    // BounDary
                    .setCountry(Country.BD)
                    .add()
                    .newVoltageLevel()
                    .setId(namingStrategy().getId("VoltageLevel", vlId))
                    .setName(vlName)
                    .setNominalV(nominalV)
                    .setTopologyKind(TopologyKind.BUS_BREAKER)
                    .add();
        }

        public void startLinesConversion() {
            countLines = 0;
            countLinesWithSvPowerFlowsAtEnds = 0;
        }

        public void anotherLineConversion(ACLineSegmentConversion c) {
            countLines++;
            if (c.terminalPowerFlow(1).defined() && c.terminalPowerFlow(2).defined()) {
                countLinesWithSvPowerFlowsAtEnds++;
            }
        }

        public void endLinesConversion() {
            String enough = countLinesWithSvPowerFlowsAtEnds < countLines ? "FEW" : "ENOUGH";
            LOG.info("{} lines with SvPowerFlow values at ends: {} / {}",
                    enough,
                    countLinesWithSvPowerFlowsAtEnds,
                    countLines);
        }

        public void invalid(String what, String reason) {
            LOG.warn("Invalid {}. Reason: {}", what, reason);
        }

        public void ignored(String what, String reason) {
            LOG.info("Ignored {}. Reason: {}", what, reason);
        }

        public void pending(String what, String reason) {
            LOG.debug("PENDING {}. Reason: {}", what, reason);
        }

        public void fixed(String what, String reason) {
            LOG.info("Fixed {}. Reason: {}", what, reason);
        }

        public void fixed(String what, String reason, double wrong, double fixed) {
            LOG.info("Fixed {}. Reason: {}. Wrong {}, fixed {}", what, reason, wrong, fixed);
        }

        public void missing(String what) {
            LOG.warn("Missing {}", what);
        }

        public void missing(String what, double defaultValue) {
            LOG.info("Missing {}. Used default value {}", what, defaultValue);
        }

        private final CgmesModel cgmes;
        private final Network network;
        private final Config config;
        private final boolean nodeBreaker;
        private final NamingStrategy namingStrategy;
        private final SubstationIdMapping substationIdMapping;
        private final Boundary boundary;
        private final TerminalMapping terminalMapping;
        private final NodeMapping nodeMapping;
        private final TapChangerTransformers tapChangerTransformers;
        private final DcMapping dcMapping;

        private int countLines;
        private int countLinesWithSvPowerFlowsAtEnds;
    }

    private final CgmesModel cgmes;
    private final Network network;
    private final Context context;

    private static final Logger LOG = LoggerFactory.getLogger(Conversion.class);

    public static final String NETWORK_PS_CGMES_MODEL_DETAIL = "CGMESModelDetail";
    public static final String NETWORK_PS_CGMES_MODEL_DETAIL_BUS_BRANCH = "bus-branch";
    public static final String NETWORK_PS_CGMES_MODEL_DETAIL_NODE_BREAKER = "node-breaker";
}
