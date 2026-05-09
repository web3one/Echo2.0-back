package com.ruoyi.quartz.task;

import com.ruoyi.bussiness.domain.TC2cOrder;
import com.ruoyi.bussiness.service.IC2cOrderService;
import com.ruoyi.common.core.redis.RedisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * C2C订单超时自动取消定时任务
 *
 * @author ruoyi
 */
@Component("c2cOrderTimeoutTask")
public class C2cOrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(C2cOrderTimeoutTask.class);

    @Resource
    private IC2cOrderService c2cOrderService;

    @Resource
    private RedisCache redisCache;

    /**
     * 检查超时未付款的C2C订单并自动取消
     * 每分钟执行一次
     */
    public void checkTimeout() {
        String lockKey = "c2c:order:timeout:lock";
        boolean locked = false;
        try {
            // 检查锁是否存在
            if (Boolean.TRUE.equals(redisCache.hasKey(lockKey))) {
                log.info("C2C timeout task is already running, skip");
                return;
            }
            // 获取锁并设置过期时间，防止死锁
            redisCache.setCacheObject(lockKey, "1", 30, TimeUnit.SECONDS);
            locked = true;

            List<TC2cOrder> timeoutOrders = c2cOrderService.selectTimeoutOrders();
            for (TC2cOrder order : timeoutOrders) {
                try {
                    c2cOrderService.handleTimeout(order);
                    log.info("C2C order timeout processed: {}", order.getOrderNo());
                } catch (Exception e) {
                    log.error("C2C order timeout error, orderId={}", order.getId(), e);
                }
            }
            if (!timeoutOrders.isEmpty()) {
                log.info("C2C timeout task completed, processed {} orders", timeoutOrders.size());
            }
        } catch (Exception e) {
            log.error("C2C timeout task error", e);
        } finally {
            if (locked) {
                redisCache.deleteObject(lockKey);
            }
        }
    }
}
