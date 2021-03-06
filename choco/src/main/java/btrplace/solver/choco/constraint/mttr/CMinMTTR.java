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

package btrplace.solver.choco.constraint.mttr;

import btrplace.model.Mapping;
import btrplace.model.Model;
import btrplace.model.VM;
import btrplace.model.constraint.MinMTTR;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ReconfigurationProblem;
import btrplace.solver.choco.SliceUtils;
import btrplace.solver.choco.constraint.ChocoConstraintBuilder;
import btrplace.solver.choco.transition.Transition;
import btrplace.solver.choco.transition.TransitionUtils;
import btrplace.solver.choco.transition.VMTransition;
import solver.Solver;
import solver.constraints.Constraint;
import solver.constraints.IntConstraintFactory;
import solver.search.limits.BacktrackCounter;
import solver.search.loop.monitors.SMF;
import solver.search.strategy.selectors.values.InDomainMin;
import solver.search.strategy.selectors.variables.InputOrder;
import solver.search.strategy.strategy.AbstractStrategy;
import solver.search.strategy.strategy.Assignment;
import solver.search.strategy.strategy.StrategiesSequencer;
import solver.variables.IntVar;
import solver.variables.VariableFactory;

import java.util.*;

/**
 * An objective that minimizes the time to repair a non-viable model.
 *
 * @author Fabien Hermenier
 */
public class CMinMTTR implements btrplace.solver.choco.constraint.CObjective {

    private List<Constraint> costConstraints;

    private boolean costActivated = false;

    private ReconfigurationProblem rp;

    /**
     * Make a new objective.
     */
    public CMinMTTR() {
        costConstraints = new ArrayList<>();
    }

    @Override
    public boolean inject(ReconfigurationProblem p) throws SolverException {
        this.rp = p;
        costActivated = false;
        List<IntVar> mttrs = new ArrayList<>();
        for (Transition m : p.getVMActions()) {
            mttrs.add(m.getEnd());
        }
        for (Transition m : p.getNodeActions()) {
            mttrs.add(m.getEnd());
        }
        IntVar[] costs = mttrs.toArray(new IntVar[mttrs.size()]);
        Solver s = p.getSolver();
        IntVar cost = VariableFactory.bounded(p.makeVarLabel("globalCost"), 0, Integer.MAX_VALUE / 100, s);

        Constraint costConstraint = IntConstraintFactory.sum(costs, cost);
        costConstraints.clear();
        costConstraints.add(costConstraint);

        p.setObjective(true, cost);

        //We set a restart limit by default, this may be useful especially with very small infrastructure
        //as the risk of cyclic dependencies increase and their is no solution for the moment to detect cycle
        //in the scheduling part
        //Restart limit = 2 * number of VMs in the DC.
        if (p.getVMs().length > 0) {
            SMF.geometrical(s, 1, 1.5d, new BacktrackCounter(p.getVMs().length * 2), Integer.MAX_VALUE);
        }
        injectPlacementHeuristic(p, cost);
        postCostConstraints();
        return true;
    }

    private void injectPlacementHeuristic(ReconfigurationProblem p, IntVar cost) {

        Model mo = p.getSourceModel();
        Mapping map = mo.getMapping();

        OnStableNodeFirst schedHeuristic = new OnStableNodeFirst(p, this);

        //Get the VMs to move
        Set<VM> onBadNodes = p.getManageableVMs();

        for (VM vm : map.getSleepingVMs()) {
            if (p.getFutureRunningVMs().contains(vm)) {
                onBadNodes.add(vm);
            }
        }

        Set<VM> onGoodNodes = map.getRunningVMs(map.getOnlineNodes());
        onGoodNodes.removeAll(onBadNodes);

        VMTransition[] goodActions = p.getVMActions(onGoodNodes);
        VMTransition[] badActions = p.getVMActions(onBadNodes);

        Solver s = p.getSolver();

        //Get the VMs to move for exclusion issue
        Set<VM> vmsToExclude = new HashSet<>(p.getManageableVMs());
        for (Iterator<VM> ite = vmsToExclude.iterator(); ite.hasNext(); ) {
            VM vm = ite.next();
            if (!(map.isRunning(vm) && p.getFutureRunningVMs().contains(vm))) {
                ite.remove();
            }
        }
        List<AbstractStrategy> strategies = new ArrayList<>();

        Map<IntVar, VM> pla = VMPlacementUtils.makePlacementMap(p);
        if (!vmsToExclude.isEmpty()) {
            strategies.add(new Assignment(new MovingVMs(p, map, vmsToExclude), new RandomVMPlacement(p, pla, true)));
        }

        placeVMs(strategies, badActions, schedHeuristic, pla);
        placeVMs(strategies, goodActions, schedHeuristic, pla);

        //VMs to run
        Set<VM> vmsToRun = new HashSet<>(map.getReadyVMs());
        vmsToRun.removeAll(p.getFutureReadyVMs());

        VMTransition[] runActions = p.getVMActions(vmsToRun);

        placeVMs(strategies, runActions, schedHeuristic, pla);

        if (p.getNodeActions().length > 0) {
            strategies.add(new Assignment(new InputOrder<>(TransitionUtils.getStarts(p.getNodeActions())), new InDomainMin()));
        }

        ///SCHEDULING PROBLEM
        MovementGraph gr = new MovementGraph(rp);
        strategies.add(new Assignment(new StartOnLeafNodes(rp, gr), new InDomainMin()));
        strategies.add(new Assignment(schedHeuristic, new InDomainMin()));

        //At this stage only it matters to plug the cost constraints
        strategies.add(new Assignment(new InputOrder<>(new IntVar[]{p.getEnd(), cost}), new InDomainMin()));

        s.getSearchLoop().set(new StrategiesSequencer(s.getEnvironment(), strategies.toArray(new AbstractStrategy[strategies.size()])));
    }

    private void placeVMs(List<AbstractStrategy> strategies, VMTransition[] actions, OnStableNodeFirst schedHeuristic, Map<IntVar, VM> map) {
        if (actions.length > 0) {
            IntVar[] hosts = SliceUtils.extractHoster(TransitionUtils.getDSlices(actions));
            if (hosts.length > 0) {
                HostingVariableSelector selectForBad = new HostingVariableSelector(hosts, schedHeuristic);
                strategies.add(new Assignment(selectForBad, new RandomVMPlacement(rp, map, true)));
            }
        }
    }

    @Override
    public Set<VM> getMisPlacedVMs(Model m) {
        return Collections.emptySet();
    }

    /**
     * Post the constraints related to the objective.
     */
    @Override
    public void postCostConstraints() {
        //TODO: Delay insertion
        if (!costActivated) {
            rp.getLogger().debug("Post the cost-oriented constraints");
            costActivated = true;
            Solver s = rp.getSolver();
            for (Constraint c : costConstraints) {
                s.post(c);
            }
            /*try {
                s.propagate();
            } catch (ContradictionException e) {
                s.setFeasible(ESat.FALSE);
                //s.setFeasible(false);
                s.post(IntConstraintFactory.FALSE(s));
            } */
        }
    }

    /**
     * Builder associated to the constraint.
     */
    public static class Builder implements ChocoConstraintBuilder {
        @Override
        public Class<? extends btrplace.model.constraint.Constraint> getKey() {
            return MinMTTR.class;
        }

        @Override
        public CMinMTTR build(btrplace.model.constraint.Constraint cstr) {
            return new CMinMTTR();
        }
    }
}
