/*
 * Copyright (c) 2012 University of Nice Sophia-Antipolis
 *
 * This file is part of btrplace.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package btrplace.plan.event;

import btrplace.model.DefaultMapping;
import btrplace.model.DefaultModel;
import btrplace.model.Mapping;
import btrplace.model.Model;
import btrplace.plan.VMStateTransition;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.UUID;

/**
 * Unit tests for {@link ForgeVM}.
 *
 * @author Fabien Hermenier
 */
public class ForgeVMTest {

    @Test
    public void testInstantiate() {
        UUID vm = UUID.randomUUID();
        ForgeVM a = new ForgeVM(vm, 3, 5);
        Assert.assertEquals(vm, a.getVM());
        Assert.assertEquals(3, a.getStart());
        Assert.assertEquals(5, a.getEnd());
        Assert.assertFalse(a.toString().contains("null"));
        Assert.assertEquals(a.getCurrentState(), VMStateTransition.VMState.init);
        Assert.assertEquals(a.getNextState(), VMStateTransition.VMState.ready);

    }

    @Test(dependsOnMethods = {"testInstantiate"})
    public void testApply() {
        Mapping map = new DefaultMapping();
        Model m = new DefaultModel(map);
        UUID vm = UUID.randomUUID();
        ForgeVM a = new ForgeVM(vm, 3, 5);
        Assert.assertTrue(a.apply(m));
        Assert.assertTrue(map.getReadyVMs().contains(vm));
        Assert.assertFalse(a.apply(m));

        UUID n = UUID.randomUUID();
        map.addOnlineNode(n);
        map.addRunningVM(vm, n);
        Assert.assertFalse(a.apply(m));
        Assert.assertTrue(map.getRunningVMs().contains(vm));

        map.addSleepingVM(vm, n);
        Assert.assertFalse(a.apply(m));
        Assert.assertTrue(map.getSleepingVMs().contains(vm));

    }

    @Test(dependsOnMethods = {"testInstantiate"})
    public void testEquals() {
        UUID vm = UUID.randomUUID();
        ForgeVM a = new ForgeVM(vm, 3, 5);
        ForgeVM b = new ForgeVM(vm, 3, 5);
        Assert.assertFalse(a.equals(new Object()));
        Assert.assertTrue(a.equals(a));
        Assert.assertEquals(a, b);
        Assert.assertEquals(a.hashCode(), b.hashCode());
        Assert.assertNotSame(a, new ForgeVM(vm, 4, 5));
        Assert.assertNotSame(a, new ForgeVM(vm, 3, 4));
        Assert.assertNotSame(a, new ForgeVM(UUID.randomUUID(), 3, 5));
    }
}