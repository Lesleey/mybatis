package demo.test;

import java.lang.reflect.Method;

/**
 * @author Lesleey
 * @date 2019/12/8-10:52
 * @function
 */
public class InterceptorImpl implements Interceptor {
    @Override
    public void before(Object proxy, Object target, Method method, Object[] args) {
        System.out.println("这是方法前逻辑");
    }

    @Override
    public void after(Object proxy, Object target, Method method, Object[] args) {
        System.out.println("这是方法后逻辑");

    }

    @Override
    public Object around(Object proxy, Object target, Method method, Object[] args) {
        System.out.println("这是取代的方法");
        return null;
    }
}
