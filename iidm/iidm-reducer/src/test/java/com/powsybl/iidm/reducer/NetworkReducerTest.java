/**
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.reducer;

import com.powsybl.iidm.export.Exporters;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.Test;

import java.nio.file.Paths;

/**
 * @author Mathieu Bague <mathieu.bague at rte-france.com>
 */
public class NetworkReducerTest {

    @Test
    public void test() {
        Network network = EurostagTutorialExample1Factory.create();

        NetworkReducer reducer = new EurostagNetworkReducer();
        reducer.reduce(network);

        Exporters.export("XIIDM", network, null, Paths.get("/tmp/eurostag-reduced.xiidm"));
    }
}
