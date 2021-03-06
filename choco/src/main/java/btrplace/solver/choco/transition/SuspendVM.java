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

import btrplace.model.Node;
import btrplace.model.VM;
import btrplace.model.VMState;
import btrplace.plan.ReconfigurationPlan;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ReconfigurationProblem;
import btrplace.solver.choco.Slice;
import btrplace.solver.choco.SliceBuilder;
import solver.constraints.IntConstraintFactory;
import solver.variables.BoolVar;
import solver.variables.IntVar;
import solver.variables.VariableFactory;


/**
 * Model an action where a running VM goes into the sleeping state through a {@link btrplace.plan.event.SuspendVM} action.
 * An estimation of the action duration must be provided through a
 * {@link btrplace.solver.choco.duration.ActionDurationEvaluator} accessible from
 * {@link btrplace.solver.choco.ReconfigurationProblem#getDurationEvaluators()} with the key {@code SuspendVM.class}
 * <p/>
 * If the reconfiguration problem has a solution, a {@link btrplace.plan.event.SuspendVM} action is inserted into the resulting
 * reconfiguration plan.
 *
 * @author Fabien Hermenier
 */
public class SuspendVM implements VMTransition {

    private Slice cSlice;

    private IntVar start;

    private IntVar duration;

    private VM vm;

    private ReconfigurationProblem rp;

    private BoolVar state;

    /**
     * Make a new model.
     *
     * @param p the RP to use as a basis.
     * @param e the VM managed by the action
     * @throws SolverException if an error occurred
     */
    public SuspendVM(ReconfigurationProblem p, VM e) throws SolverException {
        this.rp = p;
        this.vm = e;

        int d = p.getDurationEvaluators().evaluate(p.getSourceModel(), btrplace.plan.event.SuspendVM.class, e);

        duration = p.makeDuration(d, d, "suspendVM(", e, ").duration");
        this.cSlice = new SliceBuilder(p, e, "suspendVM(" + e + ").cSlice").setHoster(p.getCurrentVMLocation(p.getVM(e)))
                .setEnd(p.makeDuration(p.getEnd().getUB(), d, "suspendVM(", e, ").cSlice_end"))
                .build();
        start = VariableFactory.offset(cSlice.getEnd(), -d);
        state = VariableFactory.zero(rp.getSolver());
        rp.getSolver().post(IntConstraintFactory.arithm(cSlice.getEnd(), "<=", p.getEnd()));
    }

    @Override
    public boolean isManaged() {
        return true;
    }

    @Override
    public boolean insertActions(ReconfigurationPlan plan) {
        Node node = rp.getNode(cSlice.getHoster().getValue());
        plan.add(new btrplace.plan.event.SuspendVM(vm, node, node, start.getValue(), getEnd().getValue()));
        return true;
    }

    @Override
    public IntVar getStart() {
        return start;
    }

    @Override
    public IntVar getEnd() {
        return cSlice.getEnd();
    }

    @Override
    public IntVar getDuration() {
        return duration;
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
    public BoolVar getState() {
        return state;
    }

    @Override
    public VM getVM() {
        return vm;
    }

    /**
     * The builder devoted to a running->sleeping transition.
     */
    public static class Builder extends VMTransitionBuilder {

        /**
         * New builder
         */
        public Builder() {
            super("suspend", VMState.RUNNING, VMState.SLEEPING);
        }

        @Override
        public VMTransition build(ReconfigurationProblem r, VM v) throws SolverException {
            return new SuspendVM(r, v);
        }
    }
}
