package demo.test;

import java.lang.reflect.Method;

/**
 * @author Lesleey
 * @date 2019/12/8-10:50
 * @function
 */
public interface Interceptor {
    void before(Object proxy, Object target, Method method, Object[] args);
    void after(Object proxy, Object target, Method method, Object[] args);
    Object around(Object proxy, Object target, Method method, Object[] args);
}
