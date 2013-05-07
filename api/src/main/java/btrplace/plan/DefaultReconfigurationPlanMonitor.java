package btrplace.plan;

import btrplace.model.Model;
import btrplace.plan.event.Action;

import java.util.*;

/**
 * Simulated execution of a {@link ReconfigurationPlan}.
 * The execution relies on the dependencies between the actions, retrieved using
 * {@link ReconfigurationPlan#getDirectDependencies(btrplace.plan.event.Action)}.
 * <p/>
 * The dependencies are updated each time an action is committed, which means the action
 * have been successfully executed.
 * <p/>
 *
 * @author Fabien Hermenier
 */
public class DefaultReconfigurationPlanMonitor implements ReconfigurationPlanMonitor {

    private ReconfigurationPlan plan;

    private Model curModel;

    private final Map<Action, Set<Dependency>> pre;

    private final Map<Action, Dependency> dependencies;

    private final Object lock;

    private int nbCommitted;

    /**
     * Make a new executor.
     *
     * @param plan the plan to execute
     */
    public DefaultReconfigurationPlanMonitor(ReconfigurationPlan plan) {
        this.plan = plan;

        pre = new HashMap<>();
        dependencies = new HashMap<>();
        lock = new Object();
        reset();
    }

    private void reset() {
        synchronized (lock) {
            curModel = plan.getOrigin().clone();
            pre.clear();
            nbCommitted = 0;
            for (Action a : plan) {
                Set<Action> dependencies = plan.getDirectDependencies(a);
                if (dependencies.isEmpty()) {
                    this.dependencies.put(a, new Dependency(a, Collections.<Action>emptySet()));
                } else {
                    Dependency dep = new Dependency(a, dependencies);
                    this.dependencies.put(a, dep);
                    for (Action x : dep.getDependencies()) {
                        Set<Dependency> pres = pre.get(x);
                        if (pres == null) {
                            pres = new HashSet<>();
                            pre.put(x, pres);
                        }
                        pres.add(dep);
                    }
                }
            }
        }
    }

    @Override
    public Model getCurrentModel() {
        return curModel;
    }

    @Override
    public Set<Action> commit(Action a) {
        Set<Action> s = new HashSet<>();
        synchronized (lock) {
            boolean ret = a.apply(curModel);
            if (!ret) {
                return null;
            }
            nbCommitted++;
            //Browse all its dependencies for the action
            Set<Dependency> deps = pre.get(a);
            if (deps != null) {
                for (Dependency dep : deps) {
                    Set<Action> actions = dep.getDependencies();
                    actions.remove(a);
                    if (actions.isEmpty()) {
                        Action x = dep.getAction();
                        s.add(x);
                    }
                }
            }
        }
        return s;
    }

    @Override
    public int getNbCommitted() {
        synchronized (lock) {
            return nbCommitted;
        }
    }

    @Override
    public boolean isBlocked(Action a) {
        synchronized (lock) {
            return !dependencies.get(a).getDependencies().isEmpty();
        }
    }

    @Override
    public ReconfigurationPlan getReconfigurationPlan() {
        return plan;
    }
}
