package demo.entity;

import java.util.List;

/**
 * @author Lesleey
 * @date 2020/10/12-18:49
 * @function
 */
public class Dept {
    private Integer id;
    private String deptName;

    private List<People> peoples;

    @Override
    public String toString() {
        return "Dept{" +
                "id=" + id +
                ", deptName='" + deptName + '\'' +
                ", peoples=" + peoples +
                '}';
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public List<People> getPeoples() {
        return peoples;
    }

    public void setPeoples(List<People> peoples) {
        this.peoples = peoples;
    }
}
