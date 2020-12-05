/*
 *    Copyright 2009-2014 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * The 2nd level cache transactional buffer.
 * 
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back. 
 * Blocking cache support has been added. Therefore any get() that returns a cache miss 
 * will be followed by a put() so any lock associated with the key can be released. 
 * 
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
/**
 * 事务缓存：提供类似数据库事务的提交、回滚操作
 *
 *   {@link #clear()}和{@link #putObject(Object, Object)} 方法类似于数据库中的删除和新增操作。
 *
 *   提供回滚和提交操作
 *
 */
public class TransactionalCache implements Cache {

  private Cache delegate;
  private boolean clearOnCommit;

  /**
   *  待添加到真实 delegate 中的键值对，回滚会被清除，提交才会真正的添加到真实的缓存中
   * */
  private Map<Object, Object> entriesToAddOnCommit;

  /**
   *  存储没有命中的缓存 key
   * */
  private Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<Object, Object>();
    this.entriesMissedInCache = new HashSet<Object>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // issue #116
    //1. 从真实的缓存获取对象
    Object object = delegate.getObject(key);
    //2. 类似于布隆过滤器,添加值为空的缓存
    if (object == null) {
      entriesMissedInCache.add(key);
    }

    // issue #146
    //3. 如果已被清空，则返回空，否则返回获取到的对象
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   *  清空待提交的事务
   * */
  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  /**
   *  提交修改
   * */
  public void commit() {
    //1. 如果调用了 clear() 方法，清除缓存
    if (clearOnCommit) {
      delegate.clear();
    }
    //2. 将新增的键值对添加到真实的缓存中
    flushPendingEntries();
    //3. 重置当前对象的属性为初始化状态
    reset();
  }

  public void rollback() {
    unlockMissedEntries();
    reset();
  }

  /**
   * 重置当前对象的属性为初始化状态
   * */
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 提交修改（新增），到真实的缓存中
   * */
  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      delegate.putObject(entry, null);
    }
  }

}
