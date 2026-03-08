package common;
import java.io.Serializable;
import java.util.Objects;

public class Location implements Serializable {
    public int x;
    public int y;

    public Location(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return x == location.x && y == location.y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}