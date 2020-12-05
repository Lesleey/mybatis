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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (first in, first out) cache decorator
 *
 * @author Clinton Begin
 */
/*
 * 最近最少使用缓存
 * 基于 LinkedHashMap 覆盖其 removeEldestEntry 方法实现。
 */
public class LruCache implements Cache {

  /**
   *  被装饰者
   * */
  private final Cache delegate;

  /**
   *  通过 LinkedHashMap 保存缓存 key
   * */
  private Map<Object, Object> keyMap;

  /**
   *  最仅最少未使用的key，已被在缓存中移除
   * */
  private Object eldestKey;

  public LruCache(Cache delegate) {
    this.delegate = delegate;
    setSize(1024);
  }



  public void setSize(final int size) {
    //在设置大小时，通过覆盖 LinkedHashMap#removeEldestEntry 方法，该方法的返回值会告诉 LinedHashMap 是否需要删除最老的 key
    //第三个参数表示按照插入还是访问进行排序，true表示按照访问顺序排序，在每次访问或者插入一个元素时，都会把元素放到链表末尾，这样
    //最老的key的就会在链表头部
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) {
            //这里没辙了，把eldestKey存入实例变量
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };

  }

  /**
   * 每次添加一个元素，都判断是否有缓存key被换出
   * */
  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    cycleKeyList(key);
  }

  /**
   *  访问一个元素时，调用以下 keyMap的get方法，使得当前访问的元素移动到末尾
   * */
  @Override
  public Object getObject(Object key) {
    keyMap.get(key);
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  /**
   *  判断是否已有key被换出
   * */
  private void cycleKeyList(Object key) {
    //1. 将新加的缓存key放到 keyMap 中
    keyMap.put(key, key);
    //2. 如果 eldestkey 不为空，说明已有缓存key被换出，则将该key和对应的缓存项从实际的缓存中移除，然后重新将 eldestkey置为null
    if (eldestKey != null) {
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

}
