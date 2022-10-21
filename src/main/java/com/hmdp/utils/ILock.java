package com.hmdp.utils;

public interface ILock {

    /**
     * 获取锁
     * @param timeoutSec 超时时间
     * @return
     */
    boolean trylock(Long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();

}
