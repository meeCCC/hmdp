package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //原始方法
    /*
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY+id;
        //1.从缓存中获取
        String jsonShop = stringRedisTemplate.opsForValue().get(key);
        //2.判断有无
        if (StrUtil.isNotBlank(jsonShop)){
            //3.存在直接返回
            Shop shop = JSONUtil.toBean(jsonShop, Shop.class);
            return Result.ok(shop);
        }
        //4.不存在去数据库查
        Shop shop = getById(id);
        String jsonStr = JSONUtil.toJsonStr(shop);
        //5.写入缓存
        stringRedisTemplate.opsForValue().set(key,jsonStr,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //6.查到返回
        return Result.ok(shop);
    }*/


    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);

        //封装解决方式
        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
         //        .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null){
            return Result.fail("错误");
        }

        return Result.ok(shop);

    }


    @Override
    @Transactional
    public Result updateByIdWithCache(Shop shop) {
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }


    //缓存穿透
    public Shop queryWithPassThrough(Long id){

        String key = CACHE_SHOP_KEY + id;

        //1查询redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //命中

        if (StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否是空值

        if (shopJson != null){
            //空值返回
            return null;
        }
        //不是空值去查数据库

        //未命中
        Shop shop = getById(id);

        if (shop != null){
            //数据库有的话写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
            return shop;
        }
        //数据库没有的话缓存空值到Redis
        stringRedisTemplate.opsForValue().set(key,"",CACHE_SHOP_TTL,TimeUnit.MINUTES);

        return null;

    }


    //互斥锁解决缓存击穿
    public Shop queryWithMutex(Long id)  {
        String key = CACHE_SHOP_KEY + id;
        //查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //命中返回
        if (StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //未命中去获取锁
        String lock_key = "lock:shop:"+id;

        boolean islock = trylock(lock_key);

        Shop shop = null;
        try {
            if (!islock){
                Thread.sleep(200);
                return queryWithPassThrough(id);
            }

            //获取成功缓存重建
            //查询数据库
            shop = getById(id);

            //判断空值
            if (shop == null){
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }

            Thread.sleep(50);
            // 重新写入缓存
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unlock(lock_key);
        }

        //返回数据
        return  shop;
    }

    //逻辑过期解决缓存击穿
    public Shop queryWithLogicalExpire(Long id){

        String key = CACHE_SHOP_KEY + id;
        //查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //未命中返回空
        if (StrUtil.isBlank(shopJson)){
            return null;
        }
        //拿出数据
        RedisData redisData = JSONUtil.toBean(shopJson,RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期返回数据
            return shop;
        }
        //过期了，拿锁
        String lock_key = "lock:shop:"+id;
        boolean isLock = trylock(lock_key);
        //判断是否有锁
        if (isLock){
            //拿到锁了，开辟线程从新写入缓存
            CACHE_REBUILD_EXECUTOR.submit(()->{

                this.saveShop2Redis(id,20L);
                //释放锁
                unlock(lock_key);

            });

            //返回数据
            return shop;
        }
        //未拿到锁，返回旧数据
        return shop;
    }


    //获取锁
    public boolean trylock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    //缓存预热
    public void saveShop2Redis(Long id, Long expireTime){
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));

        //写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }


}
