package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.RedissonCache;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //静态代码块，在类加载之前提前加载好lua配置文件，且只加载一次
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }





    //一人一机下实现优惠卷秒杀
    //有多个服务下，jvm不同，维护的锁也不同，所以锁可能会失效
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀活动还未开始");
        }
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀活动已结束");
        }
        if(seckillVoucher.getStock()<1){
            return Result.fail("优惠卷已售完");
        }
        //此处锁的是用户id
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            //Long型数据的tostring方法返回的是一个全新的字符串，所以多线程
            //过来，每个线程的用户id都是不同的，此处调用intern方法，是实现字符串的值相同
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
            *//*这里使用代理对象来启动spring的事务管理，因为spring事务管理只能对管理对象的代理对象生效
            如果直接返回createVoucherOrder方法，调用的是this对象，不是代理对象，所以此处拿到当
            前的代理对象 *//*
        }
    }*/

    //多人多服务下数据库redis业务逻辑串行实现
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀活动还未开始");
        }
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀活动已结束");
        }
        if(seckillVoucher.getStock()<1){
            return Result.fail("优惠卷已售完");
        }
        //此处锁的是用户id
        Long userId = UserHolder.getUser().getId();
        //手动实现
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //Redisson实现

        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean islock = lock.tryLock();

        if (!islock){
            return Result.fail("每人仅限一单");
        }


        try {
            //Long型数据的tostring方法返回的是一个全新的字符串，所以多线程
            //过来，每个线程的用户id都是不同的，此处调用intern方法，是实现字符串的值相同
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
            *//*这里使用代理对象来启动spring的事务管理，因为spring事务管理只能对管理对象的代理对象生效
            如果直接返回createVoucherOrder方法，调用的是this对象，不是代理对象，所以此处拿到当
            前的代理对象 *//*
        } finally {
            lock.unlock();
        }
    }*/


    /**
     * 执行步骤：
     * 首先redis判断是否具有购买条件，然后返回结果到seckillVoucher()方法中，在这个方法里判断返回结果，
     * 如果可以购买，就返回订单id，同时在这个类启动时，就开启了异步线程，执行VoucherOrderHandler这
     * 个类用来读取消息队列中的消息，并将其存储到数据库中，如果出现问题，会调用该类中的handlePendingList方法
     * 这个方法可以去读取消息队列中的pendinglist中的消息
     */

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }



    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    //从消息队列拿数据
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()){
                        continue;
                    }
                    //拿到集合里的元素
                    MapRecord<String, Object, Object> entries = list.get(0);
                    //转化成map集合
                    Map<Object, Object> value = entries.getValue();
                    //利用工具转化成对象
                    VoucherOrder voucherOrder =
                            BeanUtil.fillBeanWithMap(value,new VoucherOrder(), true);

                    //创建订单
                    createVoucherOrder(voucherOrder);

                    //XACK确认消费了信息
                    stringRedisTemplate.opsForStream().
                            acknowledge("s1", "g1", entries.getId());

                }catch (Exception e){
                    log.error("处理订单异常",e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while(true){

                try {
                    //从消息队列拿数据
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1  STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    if (list == null || list.isEmpty()){
                        break;
                    }
                    //拿到集合里的元素
                    MapRecord<String, Object, Object> entries = list.get(0);
                    //转化成map集合
                    Map<Object, Object> value = entries.getValue();
                    //利用工具转化成对象
                    VoucherOrder voucherOrder =
                            BeanUtil.fillBeanWithMap(value,new VoucherOrder(), true);
                    //创建订单
                    createVoucherOrder(voucherOrder);

                    //XACK确认消费了信息
                    stringRedisTemplate.opsForStream().
                            acknowledge("s1", "g1", entries.getId());

                }catch (Exception e){
                    log.error("处理订单异常",e);
                }
            }
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long orderId = redisIdWorker.nextId("order");
        Long userId = UserHolder.getUser().getId();
        //参数：脚本文件,keys集合,没有就用空集合,参数集合
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), orderId.toString());
        //获取lua执行之后的结果
        int r = result.intValue();
        if (r != 0){
            return Result.fail(r == 1 ? "库存不足111" : "已下单");
        }
        //此时成功返回订单id，与数据库的交互转由异步线程处理
        return Result.ok(orderId);
    }


    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();

        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean islock = lock.tryLock();

        if(!islock){
            log.error("不能重复下单");
        }

        try {
            //先判断是否购买过在删减库存
            int count = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .count();

            if(count > 0){
                log.error("每人仅限一单");
                return;
            }

            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1 ")
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock",0)
                    .update();

            if (!success){
                log.error("库存不足");
                return;
            }

            //条件判断完毕，最后向数据库写入订单数据
            save(voucherOrder);
        } finally {
            lock.unlock();
        }

    }
}
