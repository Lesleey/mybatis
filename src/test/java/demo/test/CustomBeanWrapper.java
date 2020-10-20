package demo.test;

import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.submitted.custom_collection_handling.CustomCollection;
import org.apache.ibatis.submitted.custom_collection_handling.CustomObjectWrapper;

/**
 * @author Lesleey
 * @date 2019/11/24-12:43
 * @function
 */
public class CustomBeanWrapper extends CustomObjectWrapper {
    public CustomBeanWrapper(CustomCollection collection) {
        super(collection);
    }
}
