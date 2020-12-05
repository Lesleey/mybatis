package demo.entity;

import java.util.List;

/**
 * @author Lesleey
 * @date 2020/11/30-20:02
 * @function
 */
public class User {

    private Integer id;
    private String name;
    private List<String> groups;
    private List<String> roles;

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", groups=" + groups +
                ", roles=" + roles +
                '}';
    }
}
