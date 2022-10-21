package com.hmdp.service.impl;

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
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
    private StringRedisTemplate stringRedisTemplate;


    //一人一机下实现优惠卷秒杀
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


    @Override
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

        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        boolean islock = lock.trylock(1200L);

        if (!islock){
            return Result.fail("每人仅限一单");
        }


        try {
            //Long型数据的tostring方法返回的是一个全新的字符串，所以多线程
            //过来，每个线程的用户id都是不同的，此处调用intern方法，是实现字符串的值相同
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
            /*这里使用代理对象来启动spring的事务管理，因为spring事务管理只能对管理对象的代理对象生效
            如果直接返回createVoucherOrder方法，调用的是this对象，不是代理对象，所以此处拿到当
            前的代理对象 */
        } finally {
            lock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        Long userId = UserHolder.getUser().getId();
        //先判断是否购买过在删减库存
        int count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();

        if(count > 0){
            return Result.fail("每人仅限一单");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1 ")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();

        if (!success){
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        //生成订单Id
        //此处为全局id生成器，所以交给spring管理
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        this.save(voucherOrder);

        return Result.ok(orderId);
    }
}
