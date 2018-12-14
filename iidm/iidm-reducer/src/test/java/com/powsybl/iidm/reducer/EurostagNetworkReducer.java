/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.reducer;

import com.powsybl.iidm.network.*;

/**
 * @author Mathieu Bague <mathieu.bague at rte-france.com>
 */
class EurostagNetworkReducer extends AbstractNetworkReducer {

    EurostagNetworkReducer() {
        super(new EurostagNetworkFilter());
    }

    @Override
    protected void reduce(Substation substation) {
        substation.remove();
    }

    @Override
    protected void reduce(VoltageLevel voltageLevel) {
        voltageLevel.remove();
    }

    @Override
    protected void reduce(Line line) {
        Terminal terminal1 = line.getTerminal1();
        Terminal terminal2 = line.getTerminal2();
        VoltageLevel vl1 = terminal1.getVoltageLevel();

        LoadAdder loadAdder = vl1.newLoad()
                .setId(line.getId())
                .setName(line.getName())
                .setLoadType(LoadType.FICTITIOUS);

        Terminal terminal = getFilter().accept(vl1) ? terminal1 : terminal2;
        fillAdder(loadAdder, terminal);

        line.remove();
        loadAdder.add();
    }

    private void fillAdder(LoadAdder adder, Terminal terminal) {
        double p0 = Double.isNaN(terminal.getP()) ? 0.0 : terminal.getP();
        double q0 = Double.isNaN(terminal.getQ()) ? 0.0 : terminal.getQ();

        adder.setP0(p0).setQ0(q0);

        if (terminal.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER) {
            adder.setNode(terminal.getNodeBreakerView().getNode());
        } else {
            adder.setBus(terminal.getBusBreakerView().getBus().getId());
            adder.setConnectableBus(terminal.getBusBreakerView().getConnectableBus().getId());
        }
    }

    @Override
    protected void reduce(TwoWindingsTransformer transformer) {
        // Nothing to do
    }

    @Override
    protected void reduce(ThreeWindingsTransformer transformer) {
        // Nothing to do
    }

    @Override
    protected void reduce(HvdcLine hvdcLine) {
        // Nothing to do
    }
}
