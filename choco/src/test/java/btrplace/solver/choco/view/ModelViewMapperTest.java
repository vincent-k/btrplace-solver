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
import btrplace.model.view.ModelView;
import btrplace.model.view.ShareableResource;
import btrplace.plan.ReconfigurationPlan;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ReconfigurationProblem;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Collections;


/**
 * Unit tests for {@link btrplace.solver.choco.view.ModelViewMapper}.
 *
 * @author Fabien Hermenier
 */
public class ModelViewMapperTest {

    @Test
    public void testInstantiate() {
        ModelViewMapper map = ModelViewMapper.newBundle();
        Assert.assertTrue(map.isRegistered(ShareableResource.class));
        Assert.assertTrue(map.getBuilder(ShareableResource.class) instanceof CShareableResource.Builder);
    }

    @Test(dependsOnMethods = {"testInstantiate"})
    public void testRegister() {
        ModelViewMapper map = new ModelViewMapper();
        Builder cb = new Builder();
        Assert.assertTrue(map.register(cb));
        Assert.assertEquals(map.getBuilder(MockModelView.class), cb);
    }

    @Test(dependsOnMethods = {"testInstantiate", "testRegister"})
    public void testUnregister() {
        ModelViewMapper map = new ModelViewMapper();
        Assert.assertNull(map.getBuilder(MockModelView.class));
        Assert.assertFalse(map.unRegister(MockModelView.class));
    }

    public static class CMockView implements ChocoView {
        @Override
        public String getIdentifier() {
            return "mock";
        }

        @Override
        public boolean beforeSolve(ReconfigurationProblem rp) {
            return true;
        }

        @Override
        public boolean insertActions(ReconfigurationProblem rp, ReconfigurationPlan p) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cloneVM(VM vm, VM clone) {
            throw new UnsupportedOperationException();
        }
    }

    public class Builder implements ChocoModelViewBuilder {
        @Override
        public Class<? extends ModelView> getKey() {
            return MockModelView.class;
        }

        @Override
        public SolverViewBuilder build(ModelView v) throws SolverException {
            return new DelegatedBuilder("mock", Collections.<String>emptyList()) {
                @Override
                public ChocoView build(ReconfigurationProblem rp) throws SolverException {
                    return new CMockView();
                }
            };
        }

    }

    public static class MockModelView implements ModelView {

        @Override
        public String getIdentifier() {
            return "mock";
        }

        @Override
        public ModelView clone() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean substituteVM(VM curId, VM nextId) {
            throw new UnsupportedOperationException();
        }
    }
}
