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
import btrplace.plan.event.Action;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ChocoReconfigurationAlgorithmParams;
import btrplace.solver.choco.DefaultChocoReconfigurationAlgorithmParams;
import btrplace.solver.choco.DefaultReconfigurationProblemBuilder;
import btrplace.solver.choco.ReconfigurationProblem;
import btrplace.solver.choco.duration.ConstantActionDuration;
import btrplace.solver.choco.duration.DurationEvaluators;
import org.testng.Assert;
import org.testng.annotations.Test;
import solver.Cause;
import solver.Solver;
import solver.constraints.IntConstraintFactory;
import solver.exception.ContradictionException;

import java.util.HashSet;
import java.util.Iterator;


/**
 * Unit tests for {@link SuspendVM}.
 *
 * @author Fabien Hermenier
 */
public class SuspendVMTest {

    @Test
    public void testBasic() throws ContradictionException, SolverException {
        Model mo = new DefaultModel();
        VM vm1 = mo.newVM();
        Node n1 = mo.newNode();

        Mapping map = mo.getMapping();
        map.addOnlineNode(n1);
        map.addRunningVM(vm1, n1);

        ChocoReconfigurationAlgorithmParams ps = new DefaultChocoReconfigurationAlgorithmParams();
        DurationEvaluators dev = ps.getDurationEvaluators();
        dev.register(btrplace.plan.event.SuspendVM.class, new ConstantActionDuration(5));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setParams(ps)
                .setNextVMsStates(new HashSet<VM>(), new HashSet<VM>(), map.getAllVMs(), new HashSet<VM>())
                .build();
        rp.getNodeActions()[0].getState().instantiateTo(1, Cause.Null);
        SuspendVM m = (SuspendVM) rp.getVMActions()[0];
        Assert.assertEquals(vm1, m.getVM());
        Assert.assertNull(m.getDSlice());
        Assert.assertTrue(m.getDuration().instantiatedTo(5));
        Assert.assertTrue(m.getState().instantiatedTo(0));
        Assert.assertTrue(m.getCSlice().getHoster().instantiatedTo(0));

        ReconfigurationPlan p = rp.solve(0, false);
        btrplace.plan.event.SuspendVM a = (btrplace.plan.event.SuspendVM) p.getActions().iterator().next();
        Assert.assertEquals(n1, a.getSourceNode());
        Assert.assertEquals(vm1, a.getVM());
        Assert.assertEquals(5, a.getEnd() - a.getStart());
    }

    /**
     * Test that check that the action duration is lesser than
     * the cSlice duration. This allows actions scheduling
     * In practice, for this test, 2 suspend actions have to be executed sequentially
     */
    @Test
    public void testSuspendSequences() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        VM vm1 = mo.newVM();
        VM vm2 = mo.newVM();
        Node n1 = mo.newNode();

        Mapping map = mo.getMapping();
        map.addOnlineNode(n1);
        map.addRunningVM(vm1, n1);
        map.addRunningVM(vm2, n1);

        ChocoReconfigurationAlgorithmParams ps = new DefaultChocoReconfigurationAlgorithmParams();
        DurationEvaluators dev = ps.getDurationEvaluators();
        dev.register(btrplace.plan.event.SuspendVM.class, new ConstantActionDuration(5));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setParams(ps)
                .setNextVMsStates(new HashSet<VM>(), new HashSet<VM>(), map.getAllVMs(), new HashSet<VM>())
                .build();
        SuspendVM m1 = (SuspendVM) rp.getVMActions()[rp.getVM(vm1)];
        SuspendVM m2 = (SuspendVM) rp.getVMActions()[rp.getVM(vm2)];
        rp.getNodeActions()[0].getState().instantiateTo(1, Cause.Null);
        Solver s = rp.getSolver();
        s.post(IntConstraintFactory.arithm(m2.getStart(), ">=", m1.getEnd()));

        ReconfigurationPlan p = rp.solve(0, false);

        Assert.assertNotNull(p);
        Iterator<Action> ite = p.iterator();
        btrplace.plan.event.SuspendVM b1 = (btrplace.plan.event.SuspendVM) ite.next();
        btrplace.plan.event.SuspendVM b2 = (btrplace.plan.event.SuspendVM) ite.next();
        Assert.assertEquals(vm1, b1.getVM());
        Assert.assertEquals(vm2, b2.getVM());
        Assert.assertTrue(b1.getEnd() <= b2.getStart());
        Assert.assertEquals(5, b1.getEnd() - b1.getStart());
        Assert.assertEquals(5, b2.getEnd() - b2.getStart());

    }
}
