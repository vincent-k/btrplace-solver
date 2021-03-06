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

import solver.search.strategy.selectors.variables.InputOrder;
import solver.variables.IntVar;

/**
 * A variable selector that focuses the placement variables of slices.
 * Choco will try to instantiate the variables following the slice ordering
 *
 * @author Fabien Hermenier
 */
public class HostingVariableSelector extends InputOrder<IntVar> {

    private OnStableNodeFirst schedHeuristic;

    /**
     * Make a new heuristic.
     * By default, the heuristic doesn't touch the scheduling constraints.
     *
     * @param hosts the variables denoting the VMs next host
     * @param sched the scheduling heuristic to notify when the placement is invalidated
     */
    public HostingVariableSelector(IntVar[] hosts, OnStableNodeFirst sched) {
        super(hosts);
        this.schedHeuristic = sched;
    }

    @Override
    public IntVar getVariable() {
        IntVar v = super.getVariable();
        if (schedHeuristic != null) {
            schedHeuristic.invalidPlacement();
        }
        return v;
    }

}
