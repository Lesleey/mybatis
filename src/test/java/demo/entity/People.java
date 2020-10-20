package demo.entity;

import java.util.List;

/**
 * @author Lesleey
 * @date 2019/11/26-21:06
 * @function
 */
public class People {
    private long id;
    private String note;
    private Dept dept;

    public People() {
    }


    public People(String note) {
        this.note = note;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "People{" +
                "id=" + id +
                ", note='" + note + '\'' +
                ", dept=" + dept +
                '}';
    }
}
