package com.dp.utils;

public interface ILock {
    /**
     * @param timeoutSec 获取锁的超时时间
     * @return true 表示获取锁成功
     */
    boolean tryLock(long timeoutSec);

    void unlock();
}
