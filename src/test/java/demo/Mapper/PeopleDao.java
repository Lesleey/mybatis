package demo.Mapper;

import demo.entity.Dept;
import demo.entity.People;
import demo.entity.User;

import java.util.List;

/**
 * @author Lesleey
 * @date 2020/10/14-19:54
 * @function
 */
public interface PeopleDao {
    /**
     * 查询所有用户
     * */
    public List<Dept> queryAll();

    public List<User> getAllUsers();
}
