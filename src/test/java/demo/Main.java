package demo;

import demo.Mapper.PeopleDao;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Lesleey
 * @date 2019/11/23-16:40
 * @function
 */
public class Main {
    public static void main(String[] args) {
        String resource= "resources/Mybatis-conf.xml";
        InputStream inputStream=null;
        try {
            inputStream = Resources.getResourceAsStream(resource);
        } catch (IOException e) {
        }
        SqlSessionFactory sqlSessionFactory=null;
        sqlSessionFactory=new SqlSessionFactoryBuilder().build(inputStream);
        SqlSession sqlSession=null;
        try {
            sqlSession=sqlSessionFactory.openSession();
            PeopleDao peopleDao=sqlSession.getMapper(PeopleDao.class);
            peopleDao.queryAll().forEach(System.out::println);

            sqlSession.commit();

        } catch (Exception e) {
            // TODO Auto-generated catch block
            sqlSession.rollback();
            e.printStackTrace();
        }finally {
            sqlSession.close();
        }
    }

}
