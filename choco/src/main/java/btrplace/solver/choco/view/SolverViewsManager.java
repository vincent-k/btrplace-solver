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

package btrplace.solver.choco.view;

import btrplace.model.VM;
import btrplace.plan.ReconfigurationPlan;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ReconfigurationProblem;

import java.util.*;

/**
 * A tool to manage views according to their dependencies.
 *
 * @author Fabien Hermenier
 */
public class SolverViewsManager {

    private ReconfigurationProblem rp;

    private List<ChocoView> workflow;

    private Map<String, ChocoView> views;

    /**
     * New manager.
     *
     * @param p the problem to consider
     */
    public SolverViewsManager(ReconfigurationProblem p) {
        this.rp = p;
        workflow = new ArrayList<>();
        this.views = new HashMap<>();
    }

    /**
     * Build all the constraints.
     * The building is done according to the dependencies between the constraints.
     *
     * @param vs the builders to call
     * @throws SolverException if an error occurred while building a constraint or if there is a cycle of dependencies.
     */
    public void build(List<SolverViewBuilder> vs) throws SolverException {
        Set<String> done = new HashSet<>();
        List<SolverViewBuilder> remaining = new ArrayList<>(vs);

        while (!remaining.isEmpty()) {
            ListIterator<SolverViewBuilder> ite = remaining.listIterator();
            boolean blocked = true;
            while (ite.hasNext()) {
                SolverViewBuilder s = ite.next();
                if (done.containsAll(s.getDependencies())) {
                    ChocoView v = s.build(rp);
                    workflow.add(v);
                    views.put(v.getIdentifier(), v);
                    ite.remove();
                    done.add(s.getKey());
                    blocked = false;
                }
            }
            if (blocked) {
                throw new SolverException(rp.getSourceModel(), "Cyclic dependencies among the following views: " + remaining);
            }
        }
    }

    /**
     * Call {@link btrplace.solver.choco.view.ChocoView#beforeSolve(btrplace.solver.choco.ReconfigurationProblem)}
     * on each of the views in the <b>reverse order</b> of their dependencies.
     *
     * @return {@code false} if it is sure the problem does not have a solution
     * @throws SolverException if an error occurred
     */
    public boolean beforeSolve() throws SolverException {
        ListIterator<ChocoView> l = workflow.listIterator(workflow.size());
        while (l.hasPrevious()) {
            ChocoView v = l.previous();
            if (!v.beforeSolve(rp)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Call {@link btrplace.solver.choco.view.ChocoView#insertActions(btrplace.solver.choco.ReconfigurationProblem, btrplace.plan.ReconfigurationPlan)}
     * on each of the views in the <b>order</b> of their dependencies.
     *
     * @return {@code true}
     * @throws SolverException if an error occurred
     */
    public boolean insertActions(ReconfigurationPlan p) throws SolverException {
        for (ChocoView v : workflow) {
            v.insertActions(rp, p);
        }
        return true;
    }

    /**
     * Call {@link btrplace.solver.choco.view.ChocoView#cloneVM(btrplace.model.VM, btrplace.model.VM)}
     * on each of the views in the <b>order</b> of their dependencies.
     *
     * @param vm    the old VM identifier
     * @param newVM the new VM identifier
     */
    public void cloneVM(VM vm, VM newVM) {
        for (ChocoView v : workflow) {
            v.cloneVM(vm, newVM);
        }
    }

    /**
     * Add a view. the view identifier must not be already known.
     *
     * @param v the view to add.
     * @return {@code true} if the view has been added. {@code false} otherwise
     */
    public boolean add(ChocoView v) {
        if (views.put(v.getIdentifier(), v) == null) {
            workflow.add(v);
            return true;
        }
        return false;
    }

    /**
     * Get all the views keys.
     *
     * @return a set that can be empty.
     */
    public Set<String> getKeys() {
        return views.keySet();
    }

    /**
     * Get a view.
     *
     * @param id the view identifier.
     * @return the associated view if exists, {@code null} otherwise
     */
    public ChocoView get(String id) {
        return views.get(id);
    }
}
