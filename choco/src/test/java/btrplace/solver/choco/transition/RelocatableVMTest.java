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
import btrplace.model.constraint.Online;
import btrplace.model.constraint.Overbook;
import btrplace.model.constraint.Preserve;
import btrplace.model.constraint.SatConstraint;
import btrplace.model.view.ShareableResource;
import btrplace.plan.ReconfigurationPlan;
import btrplace.plan.event.Action;
import btrplace.plan.event.MigrateVM;
import btrplace.solver.SolverException;
import btrplace.solver.choco.*;
import btrplace.solver.choco.constraint.mttr.CMinMTTR;
import btrplace.solver.choco.duration.ConstantActionDuration;
import btrplace.solver.choco.duration.DurationEvaluators;
import org.testng.Assert;
import org.testng.annotations.Test;
import solver.Cause;
import solver.Solver;
import solver.constraints.IntConstraintFactory;
import solver.exception.ContradictionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Unit tests for {@link RelocatableVM}.
 *
 * @author Fabien Hermenier
 */
public class RelocatableVMTest {

    @Test
    public void testForcedToMove() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        final VM vm1 = mo.newVM();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addRunningVM(vm1, n1);

        ChocoReconfigurationAlgorithmParams ps = new DefaultChocoReconfigurationAlgorithmParams();
        DurationEvaluators dev = ps.getDurationEvaluators();
        dev.register(MigrateVM.class, new ConstantActionDuration(5));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setNextVMsStates(Collections.<VM>emptySet(), map.getAllVMs(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .setParams(ps)
                .build();
        rp.getNodeActions()[0].getState().instantiateTo(1, Cause.Null);
        rp.getNodeActions()[1].getState().instantiateTo(1, Cause.Null);
        RelocatableVM am = (RelocatableVM) rp.getVMAction(vm1);
        Assert.assertTrue(am.getRelocationMethod().instantiatedTo(0));
        Assert.assertEquals(vm1, am.getVM());
        Assert.assertEquals(2, am.getDuration().getDomainSize());
        Assert.assertEquals(0, am.getDuration().getLB());
        Assert.assertEquals(5, am.getDuration().getUB());
        Assert.assertFalse(am.getStart().instantiated());
        Assert.assertFalse(am.getEnd().instantiated());
        Assert.assertNotNull(am.getCSlice());
        Assert.assertTrue(am.getCSlice().getHoster().instantiatedTo(rp.getNode(n1)));
        Assert.assertTrue(am.getState().instantiatedTo(1));
        Assert.assertNotNull(am.getDSlice());
        Assert.assertFalse(am.getDSlice().getHoster().instantiated());

        //No VMs on n1, discrete mode
        Solver s = rp.getSolver();

        s.post(IntConstraintFactory.arithm(rp.getNbRunningVMs()[rp.getNode(n1)], "=", 0));
        System.out.println(s);
        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        System.out.println(p);
        Model m = p.getResult();
        Assert.assertEquals(n2, m.getMapping().getVMLocation(vm1));

        MigrateVM a = (MigrateVM) p.getActions().iterator().next();
        Assert.assertEquals(0, a.getStart());
        Assert.assertEquals(5, a.getEnd());
        Assert.assertEquals(n1, a.getSourceNode());
        Assert.assertEquals(n2, a.getDestinationNode());
        Assert.assertEquals(vm1, a.getVM());
    }

    @Test
    public void testForcedToStay() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        final VM vm1 = mo.newVM();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addRunningVM(vm1, n1);

        ChocoReconfigurationAlgorithmParams ps = new DefaultChocoReconfigurationAlgorithmParams();
        DurationEvaluators dev = ps.getDurationEvaluators();
        dev.register(MigrateVM.class, new ConstantActionDuration(5));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setNextVMsStates(Collections.<VM>emptySet(), map.getAllVMs(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .setParams(ps)
                .build();
        rp.getNodeActions()[0].getState().instantiateTo(1, Cause.Null);
        rp.getNodeActions()[1].getState().instantiateTo(1, Cause.Null);
        RelocatableVM am = (RelocatableVM) rp.getVMAction(vm1);

        //No VMs on n2
        rp.getNbRunningVMs()[rp.getNode(n2)].instantiateTo(0, Cause.Null);

        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        Assert.assertEquals(0, p.getSize());

        Assert.assertTrue(am.getDuration().instantiatedTo(0));
        Assert.assertTrue(am.getDSlice().getHoster().instantiatedTo(rp.getNode(n1)));
        Assert.assertTrue(am.getStart().instantiatedTo(0));
        Assert.assertTrue(am.getEnd().instantiatedTo(0));


        Model m = p.getResult();
        Assert.assertEquals(n1, m.getMapping().getVMLocation(vm1));
    }

    @Test
    public void testRelocateDueToPreserve() throws SolverException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();

        final VM vm1 = mo.newVM();
        final VM vm2 = mo.newVM();
        final VM vm3 = mo.newVM();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addRunningVM(vm1, n1);
        map.addRunningVM(vm2, n1);
        map.addRunningVM(vm3, n2);
        btrplace.model.view.ShareableResource rc = new ShareableResource("cpu", 10, 10);
        rc.setCapacity(n1, 7);
        rc.setConsumption(vm1, 3);
        rc.setConsumption(vm2, 3);
        rc.setConsumption(vm3, 5);

        Preserve pr = new Preserve(vm1, "cpu", 5);
        ChocoReconfigurationAlgorithm cra = new DefaultChocoReconfigurationAlgorithm();
        mo.attach(rc);
        List<SatConstraint> cstrs = new ArrayList<>();
        cstrs.addAll(Online.newOnline(map.getAllNodes()));
        cstrs.addAll(Overbook.newOverbooks(map.getAllNodes(), "cpu", 1));
        cstrs.add(pr);
        ReconfigurationPlan p = cra.solve(mo, cstrs);
        Assert.assertNotNull(p);
    }

    /**
     * The re-instantiation is possible but will lead in a waste of time.
     *
     * @throws SolverException
     * @throws ContradictionException
     */
    @Test
    public void testNotWorthyReInstantiation() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();

        final VM vm1 = mo.newVM();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addRunningVM(vm1, n1);
        ChocoReconfigurationAlgorithmParams ps = new DefaultChocoReconfigurationAlgorithmParams();
        DurationEvaluators dev = ps.getDurationEvaluators();
        dev.register(MigrateVM.class, new ConstantActionDuration(2));

        mo.getAttributes().put(vm1, "template", "small");
        mo.getAttributes().put(vm1, "clone", true);
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setNextVMsStates(Collections.<VM>emptySet(), map.getAllVMs(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .setParams(ps)
                .build();
        RelocatableVM am = (RelocatableVM) rp.getVMAction(vm1);
        Assert.assertFalse(am.getRelocationMethod().instantiated());
    }

    /**
     * The re-instantiation is possible and worthy.
     *
     * @throws SolverException
     * @throws ContradictionException
     */
    @Test
    public void testWorthyReInstantiation() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();

        Node n1 = mo.newNode();
        Node n2 = mo.newNode();
        VM vm10 = mo.newVM();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addRunningVM(vm10, n1); //Not using vm1 because intPool starts at 0 so their will be multiple (0,1) VMs.
        ChocoReconfigurationAlgorithmParams ps = new DefaultChocoReconfigurationAlgorithmParams();
        DurationEvaluators dev = ps.getDurationEvaluators();
        dev.register(btrplace.plan.event.MigrateVM.class, new ConstantActionDuration(20));
        dev.register(btrplace.plan.event.ForgeVM.class, new ConstantActionDuration(3));
        dev.register(btrplace.plan.event.BootVM.class, new ConstantActionDuration(2));
        dev.register(btrplace.plan.event.ShutdownVM.class, new ConstantActionDuration(1));

        mo.getAttributes().put(vm10, "template", "small");
        mo.getAttributes().put(vm10, "clone", true);
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setNextVMsStates(Collections.<VM>emptySet(), map.getAllVMs(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .setParams(ps)
                .setManageableVMs(map.getAllVMs())
                .build();
        RelocatableVM am = (RelocatableVM) rp.getVMAction(vm10);
        am.getDSlice().getHoster().instantiateTo(rp.getNode(n2), Cause.Null);
        new CMinMTTR().inject(rp);

        ReconfigurationPlan p = rp.solve(10, true);
        Assert.assertNotNull(p);
        System.out.println(p);
        Assert.assertTrue(am.getRelocationMethod().instantiatedTo(1));
        Assert.assertEquals(p.getSize(), 3);
        Model res = p.getResult();
        //Check the VM has been relocated
        Assert.assertEquals(res.getMapping().getRunningVMs(n1).size(), 0);
        Assert.assertEquals(res.getMapping().getRunningVMs(n2).size(), 1);
        Assert.assertNotNull(p);
        for (Action a : p) {
            Assert.assertTrue(a.getStart() >= 0, a.toString());
            Assert.assertTrue(a.getEnd() >= a.getStart(), a.toString());
        }
    }

    /**
     * The re-instantiation is possible and worthy.
     *
     * @throws SolverException
     * @throws ContradictionException
     */
    @Test
    public void testWorthlessReInstantiation() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        final VM vm10 = mo.newVM();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();


        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addRunningVM(vm10, n1); //Not using vm1 because intPool starts at 0 so their will be multiple (0,1) VMs.
        ChocoReconfigurationAlgorithmParams ps = new DefaultChocoReconfigurationAlgorithmParams();
        DurationEvaluators dev = ps.getDurationEvaluators();
        dev.register(MigrateVM.class, new ConstantActionDuration(2));
        dev.register(btrplace.plan.event.ForgeVM.class, new ConstantActionDuration(3));
        dev.register(btrplace.plan.event.BootVM.class, new ConstantActionDuration(2));
        dev.register(btrplace.plan.event.ShutdownVM.class, new ConstantActionDuration(1));

        mo.getAttributes().put(vm10, "template", "small");
        mo.getAttributes().put(vm10, "clone", true);
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setNextVMsStates(Collections.<VM>emptySet(), map.getAllVMs(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .setParams(ps)
                .setManageableVMs(map.getAllVMs())
                .build();
        RelocatableVM am = (RelocatableVM) rp.getVMAction(vm10);
        am.getDSlice().getHoster().instantiateTo(rp.getNode(n2), Cause.Null);
        new CMinMTTR().inject(rp);
        ReconfigurationPlan p = rp.solve(10, true);
        Assert.assertNotNull(p);
        System.out.println(p);
        Assert.assertTrue(am.getRelocationMethod().instantiatedTo(0));
        Assert.assertEquals(p.getSize(), 1);
        Model res = p.getResult();
        //Check the VM has been relocated
        Assert.assertEquals(res.getMapping().getRunningVMs(n1).size(), 0);
        Assert.assertEquals(res.getMapping().getRunningVMs(n2).size(), 1);
        Assert.assertNotNull(p);
    }

    @Test
    public void testForcedReInstantiation() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();

        final VM vm10 = mo.newVM();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addRunningVM(vm10, n1); //Not using vm1 because intPool starts at 0 so their will be multiple (0,1) VMs.
        ChocoReconfigurationAlgorithmParams ps = new DefaultChocoReconfigurationAlgorithmParams();
        DurationEvaluators dev = ps.getDurationEvaluators();
        dev.register(MigrateVM.class, new ConstantActionDuration(20));
        dev.register(btrplace.plan.event.ForgeVM.class, new ConstantActionDuration(3));
        dev.register(btrplace.plan.event.BootVM.class, new ConstantActionDuration(2));
        dev.register(btrplace.plan.event.ShutdownVM.class, new ConstantActionDuration(1));

        mo.getAttributes().put(vm10, "template", "small");
        mo.getAttributes().put(vm10, "clone", true);
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setNextVMsStates(Collections.<VM>emptySet(), map.getAllVMs(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .setParams(ps)
                .setManageableVMs(map.getAllVMs())
                .build();
        RelocatableVM am = (RelocatableVM) rp.getVMAction(vm10);
        am.getRelocationMethod().instantiateTo(1, Cause.Null);
        am.getDSlice().getHoster().instantiateTo(rp.getNode(n2), Cause.Null);
        new CMinMTTR().inject(rp);
        ReconfigurationPlan p = rp.solve(10, true);
        Assert.assertNotNull(p);
        System.out.println(p);
        Assert.assertTrue(am.getRelocationMethod().instantiatedTo(1));
        Assert.assertEquals(p.getSize(), 3);
        Model res = p.getResult();
        //Check the VM has been relocated
        Assert.assertEquals(res.getMapping().getRunningVMs(n1).size(), 0);
        Assert.assertEquals(res.getMapping().getRunningVMs(n2).size(), 1);
        Assert.assertNotNull(p);

        //Check for the actions duration
        for (Action a : p) {
            if (a instanceof btrplace.plan.event.ForgeVM) {
                Assert.assertEquals(a.getEnd() - a.getStart(), 3);
            } else if (a instanceof btrplace.plan.event.ShutdownVM) {
                Assert.assertEquals(a.getEnd() - a.getStart(), 1);
            } else if (a instanceof btrplace.plan.event.BootVM) {
                Assert.assertEquals(a.getEnd() - a.getStart(), 2);
            } else {
                Assert.fail();
            }
        }
    }

    @Test
    public void testForcedMigration() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();

        final VM vm10 = mo.newVM();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addRunningVM(vm10, n1); //Not using vm1 because intPool starts at 0 so their will be multiple (0,1) VMs.
        ChocoReconfigurationAlgorithmParams ps = new DefaultChocoReconfigurationAlgorithmParams();
        DurationEvaluators dev = ps.getDurationEvaluators();
        dev.register(MigrateVM.class, new ConstantActionDuration(20));
        dev.register(btrplace.plan.event.ForgeVM.class, new ConstantActionDuration(3));
        dev.register(btrplace.plan.event.BootVM.class, new ConstantActionDuration(2));
        dev.register(btrplace.plan.event.ShutdownVM.class, new ConstantActionDuration(1));

        mo.getAttributes().put(vm10, "template", "small");
        mo.getAttributes().put(vm10, "clone", true);
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setNextVMsStates(Collections.<VM>emptySet(), map.getAllVMs(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .setParams(ps)
                .setManageableVMs(map.getAllVMs())
                .build();
        RelocatableVM am = (RelocatableVM) rp.getVMAction(vm10);
        am.getRelocationMethod().instantiateTo(0, Cause.Null);
        am.getDSlice().getHoster().instantiateTo(rp.getNode(n2), Cause.Null);
        new CMinMTTR().inject(rp);
        ReconfigurationPlan p = rp.solve(10, true);
        Assert.assertNotNull(p);
        System.out.println(p);
        Assert.assertTrue(am.getRelocationMethod().instantiatedTo(0));
        Assert.assertEquals(p.getSize(), 1);
        Model res = p.getResult();
        //Check the VM has been relocated
        Assert.assertEquals(res.getMapping().getRunningVMs(n1).size(), 0);
        Assert.assertEquals(res.getMapping().getRunningVMs(n2).size(), 1);
        Assert.assertNotNull(p);
    }

    @Test
    public void testReinstantiationWithPreserve() throws SolverException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();

        VM vm5 = mo.newVM();
        VM vm6 = mo.newVM();
        VM vm7 = mo.newVM();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addRunningVM(vm5, n1);
        map.addRunningVM(vm6, n1);
        map.addRunningVM(vm7, n2);
        ShareableResource rc = new ShareableResource("cpu", 10, 10);
        rc.setCapacity(n1, 7);
        rc.setConsumption(vm5, 3);
        rc.setConsumption(vm6, 3);
        rc.setConsumption(vm7, 5);

        for (VM vm : map.getAllVMs()) {
            mo.getAttributes().put(vm, "template", "small");
            mo.getAttributes().put(vm, "clone", true);
        }
        Preserve pr = new Preserve(vm5, "cpu", 5);
        ChocoReconfigurationAlgorithm cra = new DefaultChocoReconfigurationAlgorithm();
        cra.getDurationEvaluators().register(MigrateVM.class, new ConstantActionDuration(20));

        mo.attach(rc);
        List<SatConstraint> cstrs = new ArrayList<>();
        cstrs.addAll(Online.newOnline(map.getAllNodes()));
        cstrs.add(pr);
        cra.doOptimize(true);
        try {
            ReconfigurationPlan p = cra.solve(mo, cstrs);
            System.out.println(p);
            Assert.assertNotNull(p);
        } catch (Exception e) {
            Assert.fail(e.getMessage(), e);
        }
    }

    @Test
    public void testStayRunning() throws SolverException {

        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        final VM vm1 = mo.newVM();
        Node n1 = mo.newNode();

        map.addOnlineNode(n1);
        map.addRunningVM(vm1, n1);

        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setManageableVMs(Collections.<VM>emptySet())
                .build();
        Assert.assertEquals(rp.getVMAction(vm1).getClass(), RelocatableVM.class);
        RelocatableVM m1 = (RelocatableVM) rp.getVMAction(vm1);
        Assert.assertNotNull(m1.getCSlice());
        Assert.assertNotNull(m1.getDSlice());
        Assert.assertTrue(m1.getCSlice().getHoster().instantiatedTo(rp.getNode(n1)));
        Assert.assertTrue(m1.getDSlice().getHoster().instantiatedTo(rp.getNode(n1)));
        Assert.assertTrue(m1.getDuration().instantiatedTo(0));
        Assert.assertTrue(m1.getStart().instantiatedTo(0));
        Assert.assertTrue(m1.getEnd().instantiatedTo(0));
        System.out.println(rp.getSolver().toString());
        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        Assert.assertEquals(p.getSize(), 0);
    }
}
