package org.example;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 统一管理 working 与 sleeping 两个队列，
 * 并由单一定时任务定期扫描 sleeping 队列尝试唤醒。
 */
@Component
public class ChannelManager {

    private final Set<Channel> workingChannels = ConcurrentHashMap.newKeySet();
    private final Set<Channel> sleepingChannels = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ChannelManager-Wakeup");
                t.setDaemon(true);
                return t;
            });

    public ChannelManager() {
        // 首次延迟30秒，之后每15秒统一尝试唤醒 sleepingChannels
        scheduler.scheduleAtFixedRate(
                this::scanAndWake,
                30, 30, TimeUnit.SECONDS
        );
        scheduler.scheduleAtFixedRate(
                this::printStats,
                30, 30, TimeUnit.SECONDS
        );
    }

    public void addWorking(Channel ch) {
        sleepingChannels.remove(ch);
        workingChannels.add(ch);
    }

    public void addSleeping(Channel ch) {
        workingChannels.remove(ch);
        sleepingChannels.add(ch);
    }

    public void remove(Channel ch) {
        workingChannels.remove(ch);
        sleepingChannels.remove(ch);
    }

    private void scanAndWake() {
        for (Channel ch : sleepingChannels) {
            if (ch.isActive()) {
                ch.read();
                System.out.println("** 全局唤醒尝试: " + ch);
            } else {
                // 通道已不活跃，移除
                sleepingChannels.remove(ch);
            }
        }
    }
    private void printStats() {
        int w = workingChannels.size();
        int s = sleepingChannels.size();
        System.out.printf(
                "[ChannelManager] Working clients: %d, Sleeping clients: %d%n",
                w, s
        );
    }
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
