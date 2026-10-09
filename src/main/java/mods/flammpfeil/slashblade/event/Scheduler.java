package mods.flammpfeil.slashblade.event;

import net.minecraft.world.entity.LivingEntity;
import java.util.*;

public class Scheduler {
    @FunctionalInterface public interface Callback<T> { void handle(T context, Scheduler queue, long time); }
    private record Task(String key, long time, long sequence, Callback<LivingEntity> callback) {}
    private final PriorityQueue<Task> queue = new PriorityQueue<>(Comparator.comparingLong(Task::time).thenComparingLong(Task::sequence));
    private final Map<String, Set<Long>> pending = new HashMap<>();
    private long sequence;

    public Scheduler(){
    }

    public void onTick(LivingEntity entity){
        tick(entity, entity.level().getGameTime());
    }

    public void schedule(String key, long time, Callback<LivingEntity> callback){
        if (pending.computeIfAbsent(key, ignored -> new HashSet<>()).add(time)) queue.add(new Task(key, time, sequence++, callback));
    }
    public void tick(LivingEntity entity, long time) {
        while (!queue.isEmpty() && queue.peek().time() <= time) {
            Task task = queue.remove();
            Set<Long> times = pending.get(task.key());
            times.remove(task.time());
            if (times.isEmpty()) pending.remove(task.key());
            task.callback().handle(entity, this, time);
        }
    }
}
