/*
 * Copyright (c) 2014 University Nice Sophia Antipolis
 *
 * This file is part of btrplace.
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package btrplace.solver.choco.transition;

import btrplace.model.*;
import btrplace.plan.ReconfigurationPlan;
import btrplace.solver.SolverException;
import btrplace.solver.choco.DefaultReconfigurationProblemBuilder;
import btrplace.solver.choco.ReconfigurationProblem;
import org.testng.Assert;
import org.testng.annotations.Test;
import solver.Cause;
import solver.exception.ContradictionException;

/**
 * Unit tests for {@link StayAwayVM}.
 *
 * @author Fabien Hermenier
 */
public class StayAwayVMTest {

    @Test
    public void testBasic() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        final VM vm1 = mo.newVM();
        final VM vm2 = mo.newVM();
        Node n1 = mo.newNode();

        map.addOnlineNode(n1);
        map.addSleepingVM(vm1, n1);
        map.addReadyVM(vm2);

        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .build();

        rp.getNodeAction(n1).getState().instantiateTo(1, Cause.Null);
        StayAwayVM ma1 = (StayAwayVM) rp.getVMAction(vm1);
        StayAwayVM ma2 = (StayAwayVM) rp.getVMAction(vm2);
        Assert.assertEquals(vm1, ma1.getVM());
        Assert.assertEquals(vm2, ma2.getVM());

        for (VMTransition am : rp.getVMActions()) {
            Assert.assertTrue(am.getState().instantiatedTo(0));
            Assert.assertNull(am.getCSlice());
            Assert.assertNull(am.getDSlice());
            Assert.assertTrue(am.getStart().instantiatedTo(0));
            Assert.assertTrue(am.getEnd().instantiatedTo(0));
            Assert.assertTrue(am.getDuration().instantiatedTo(0));
        }

        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        Assert.assertEquals(0, p.getSize());
    }
}
