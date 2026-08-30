package fr.skynex.worldx.edit;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;

public class ConvexHull3D {

    public static boolean contains(double px, double py, double pz, List<Location> points) {
        if (points.size() < 4) {
            return false;
        }

        List<Face> faces = new ArrayList<>();
        int n = points.size();

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    Location a = points.get(i);
                    Location b = points.get(j);
                    Location c = points.get(k);

                    double nx = (b.getY() - a.getY()) * (c.getZ() - a.getZ()) - (b.getZ() - a.getZ()) * (c.getY() - a.getY());
                    double ny = (b.getZ() - a.getZ()) * (c.getX() - a.getX()) - (b.getX() - a.getX()) * (c.getZ() - a.getZ());
                    double nz = (b.getX() - a.getX()) * (c.getY() - a.getY()) - (b.getY() - a.getY()) * (c.getX() - a.getX());

                    double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len < 1e-6) {
                        continue;
                    }
                    nx /= len;
                    ny /= len;
                    nz /= len;

                    double d = -(nx * a.getX() + ny * a.getY() + nz * a.getZ());

                    int positiveSide = 0;
                    int negativeSide = 0;
                    for (int m = 0; m < n; m++) {
                        if (m == i || m == j || m == k) {
                            continue;
                        }
                        Location p = points.get(m);
                        double val = nx * p.getX() + ny * p.getY() + nz * p.getZ() + d;
                        if (val > 1e-4) {
                            positiveSide++;
                        } else if (val < -1e-4) {
                            negativeSide++;
                        }
                    }

                    if (positiveSide == 0 || negativeSide == 0) {
                        Location other = null;
                        for (int m = 0; m < n; m++) {
                            if (m != i && m != j && m != k) {
                                other = points.get(m);
                                break;
                            }
                        }
                        if (other != null) {
                            double val = nx * other.getX() + ny * other.getY() + nz * other.getZ() + d;
                            if (val > 0) {
                                nx = -nx;
                                ny = -ny;
                                nz = -nz;
                                d = -d;
                            }
                        }
                        faces.add(new Face(nx, ny, nz, d));
                    }
                }
            }
        }

        if (faces.isEmpty()) {
            return false;
        }

        for (Face face : faces) {
            double val = face.nx * px + face.ny * py + face.nz * pz + face.d;
            if (val > 1e-4) {
                return false;
            }
        }
        return true;
    }

    private static class Face {
        final double nx, ny, nz, d;
        Face(double nx, double ny, double nz, double d) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.d = d;
        }
    }
}
