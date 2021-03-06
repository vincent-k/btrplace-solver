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
import solver.Solver;
import solver.constraints.IntConstraintFactory;
import solver.variables.BoolVar;
import solver.variables.IntVar;
import solver.variables.VariableFactory;


/**
 * Model an action that resume a sleeping VM.
 * An estimation of the action duration must be provided through a
 * {@link btrplace.solver.choco.duration.ActionDurationEvaluator} accessible from
 * {@link btrplace.solver.choco.ReconfigurationProblem#getDurationEvaluators()} with the key {@code ResumeVM.class}
 * <p/>
 * If the reconfiguration problem has a solution, a {@link btrplace.plan.event.ResumeVM} action
 * is inserted into the resulting reconfiguration plan.
 *
 * @author Fabien Hermenier
 */
public class ResumeVM implements VMTransition {

    public static final String PREFIX = "resumeVM(";
    private VM vm;
    private ReconfigurationProblem rp;
    private IntVar start;
    private IntVar end;
    private IntVar duration;
    private Slice dSlice;
    private BoolVar state;

    /**
     * Make a new model.
     *
     * @param p the RP to use as a basis.
     * @param e the VM managed by the action
     * @throws SolverException if an error occurred
     */
    public ResumeVM(ReconfigurationProblem p, VM e) throws SolverException {
        this.rp = p;
        this.vm = e;

        int d = p.getDurationEvaluators().evaluate(p.getSourceModel(), btrplace.plan.event.ResumeVM.class, e);

        start = p.makeDuration(p.getEnd().getUB() - d, 0, PREFIX, e, ").start");
        end = VariableFactory.offset(start, d);
        duration = p.makeDuration(d, d, PREFIX, e, ").duration");
        dSlice = new SliceBuilder(p, e, PREFIX, e, ").dSlice").setStart(start)
                .setDuration(p.makeDuration(p.getEnd().getUB(), d, PREFIX, e, ").dSlice_duration"))
                .build();

        Solver s = p.getSolver();
        s.post(IntConstraintFactory.arithm(end, "<=", p.getEnd()));
        state = VariableFactory.one(rp.getSolver());
    }

    @Override
    public boolean isManaged() {
        return true;
    }

    @Override
    public boolean insertActions(ReconfigurationPlan plan) {
        int ed = end.getValue();
        int st = start.getValue();
        Node src = rp.getSourceModel().getMapping().getVMLocation(vm);
        Node dst = rp.getNode(dSlice.getHoster().getValue());
        btrplace.plan.event.ResumeVM a = new btrplace.plan.event.ResumeVM(vm, src, dst, st, ed);
        plan.add(a);
        return true;
    }

    @Override
    public VM getVM() {
        return vm;
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
        return duration;
    }

    @Override
    public Slice getCSlice() {
        return null;
    }

    @Override
    public Slice getDSlice() {
        return dSlice;
    }

    @Override
    public BoolVar getState() {
        return state;
    }

    /**
     * The builder devoted to a sleeping->running transition.
     */
    public static class Builder extends VMTransitionBuilder {

        /**
         * New builder
         */
        public Builder() {
            super("resume", VMState.SLEEPING, VMState.RUNNING);
        }

        @Override
        public VMTransition build(ReconfigurationProblem r, VM v) throws SolverException {
            return new ResumeVM(r, v);
        }
    }
}
