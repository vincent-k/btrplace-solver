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

import btrplace.model.Mapping;
import btrplace.model.Node;
import btrplace.model.VM;
import btrplace.model.VMState;
import btrplace.plan.ReconfigurationPlan;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ReconfigurationProblem;
import btrplace.solver.choco.Slice;
import btrplace.solver.choco.SliceBuilder;
import solver.variables.BoolVar;
import solver.variables.IntVar;
import solver.variables.VariableFactory;

import java.util.EnumSet;


/**
 * An action to model a VM that is killed.
 * An estimation of the action duration must be provided through a
 * {@link btrplace.solver.choco.duration.ActionDurationEvaluator} accessible from
 * {@link btrplace.solver.choco.ReconfigurationProblem#getDurationEvaluators()} with the key {@code KillVM.class}
 * <p/>
 * If the reconfiguration problem has a solution, a {@link btrplace.plan.event.KillVM} action
 * is inserted into the resulting reconfiguration plan.
 * <p/>
 * The kill necessarily occurs at the beginning of the reconfiguration process and
 * can consider a VM that is either in the ready, the running, and the sleeping state.
 *
 * @author Fabien Hermenier
 */
public class KillVM implements VMTransition {

    private VM vm;

    private Node node;

    private BoolVar state;

    private IntVar start;

    private IntVar end;

    private Slice cSlice;

    /**
     * Make a new model.
     *
     * @param rp the RP to use as a basis.
     * @param e  the VM managed by the action
     * @throws SolverException if an error occurred
     */
    public KillVM(ReconfigurationProblem rp, VM e) throws SolverException {
        vm = e;
        Mapping map = rp.getSourceModel().getMapping();
        node = map.getVMLocation(vm);
        state = VariableFactory.zero(rp.getSolver());

        int d = rp.getDurationEvaluators().evaluate(rp.getSourceModel(), btrplace.plan.event.KillVM.class, e);

        if (map.isRunning(vm)) {
            cSlice = new SliceBuilder(rp, e, "killVM('" + e + "').cSlice")
                    .setStart(rp.getStart())
                    .setHoster(rp.getCurrentVMLocation(rp.getVM(vm)))
                    .setEnd(VariableFactory.fixed(d, rp.getSolver()))
                    .build();
            end = cSlice.getEnd();
        } else {
            end = VariableFactory.fixed(d, rp.getSolver());
        }
        start = rp.getStart();
    }

    @Override
    public boolean isManaged() {
        return true;
    }

    @Override
    public IntVar getStart() {
        return start;
    }

    @Override
    public IntVar getEnd() {
        return end;
    }

    @Override
    public IntVar getDuration() {
        return end;
    }

    @Override
    public Slice getCSlice() {
        return cSlice;
    }

    @Override
    public Slice getDSlice() {
        return null;
    }

    @Override
    public VM getVM() {
        return vm;
    }

    @Override
    public boolean insertActions(ReconfigurationPlan plan) {
        plan.add(new btrplace.plan.event.KillVM(vm, node, getStart().getValue(), getEnd().getValue()));
        return true;
    }

    @Override
    public BoolVar getState() {
        return state;
    }

    /**
     * The builder devoted to a (init|ready|running|sleep)->killed transition.
     */
    public static class Builder extends VMTransitionBuilder {

        /**
         * New builder
         */
        public Builder() {
            super("kill", EnumSet.of(VMState.INIT, VMState.READY, VMState.RUNNING, VMState.SLEEPING), VMState.KILLED);
        }

        @Override
        public VMTransition build(ReconfigurationProblem r, VM v) throws SolverException {
            return new KillVM(r, v);
        }
    }
}
