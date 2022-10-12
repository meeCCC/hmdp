package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result getList() {

        String typeShop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

        if (StrUtil.isNotBlank(typeShop)){
            List<ShopType> shopTypes = JSONUtil.toList(typeShop, ShopType.class);
            return Result.ok(shopTypes);
        }

        List<ShopType> list = list();
        //转换成json
        String jsonStr = JSONUtil.toJsonStr(list);

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,jsonStr,CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return Result.ok(list);


    }
}
