package edu.smu.signalprocessing;

import uk.me.berndporr.iirj.Butterworth;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {

    public static ArrayList<String> readFile(String filePath) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            lines.add(line);
        }
        return lines;
    }

    public static int calcDirection(double... values) {
        double max = 0;
        for (double v : values) {
            if (max < Math.abs(v)) {
                max = v;
            }
        }
        return max < 0 ? -1 : 1;
    }

    public static double[] calcMagnitude(String record) {
        String[] values = record.split(",");
        double t = Double.parseDouble(values[0]);
        double x = Double.parseDouble(values[1]);
        double y = Double.parseDouble(values[2]);
        double z = Double.parseDouble(values[3]);
        double r = Math.sqrt(x * x + y * y + z * z) * calcDirection(x, y, z);
        return new double[]{t, r};
    }

    public static double[] getSingleAxis(String record, int axis) {
        String[] values = record.split(",");
        double t = Double.parseDouble(values[0]);
        double r = Double.parseDouble(values[axis]);
        return new double[]{t, r};
    }

    public static List<double[]> integrateSignal(List<double[]> signal) {
        DoubleAdder doubleAdder = new DoubleAdder();

        return IntStream
                .range(0, signal.size() - 1)
                .mapToObj(i -> {
                    double[] v1 = signal.get(i);
                    double[] v2 = signal.get(i + 1);
                    double dt = v2[0] - v1[0];
                    double da = dt * (v1[1] + v2[1]) / 2000.0;
                    doubleAdder.add(da);
                    return new double[]{v2[0], doubleAdder.sum()};
                })
                .collect(Collectors.toList());
    }

    public static List<double[]> filterSignal(List<double[]> signal, int windowSize) {
        return IntStream
                .range(0, signal.size() - windowSize)
                .mapToObj(i -> {
                    double v = signal.subList(i, i + windowSize)
                            .stream()
                            .mapToDouble(x -> x[1])
                            .sum() / windowSize;
                    return new double[]{signal.get(i)[0], v};
                })
                .collect(Collectors.toList());
    }

    public static void writeStreamToFile(List<double[]> signal, String filePath) throws FileNotFoundException {
        File csvOutputFile = new File(filePath);
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            signal.stream()
                    .map(row -> String.format("%.0f,%f", row[0], row[1]))
                    .forEach(pw::println);
        }
    }

    public static double getMeanValue(List<double[]> signal) {
        return signal.stream()
                .map(row -> row[1])
                .mapToDouble(Double::doubleValue)
                .average()
                .getAsDouble();
    }

    public static void main(String[] args) {
        final int windowSize = 10;
        final String path0 = "D:\\Projects\\signal-processing\\data\\raw-magnitude.csv";
        final String path1 = "D:\\Projects\\signal-processing\\data\\filtered-subtracted-signal.csv";
        final String path2 = "D:\\Projects\\signal-processing\\data\\filtered-integrated-signal.csv";
        final String path3 = "D:\\Projects\\signal-processing\\data\\integrated-integrated-signal.csv";
        try {
            ArrayList<String> data = readFile("D:\\Projects\\signal-processing\\data\\accel2.csv");
            List<double[]> records = data.stream()
                    .map(r -> getSingleAxis(r, 2))
                    .collect(Collectors.toList());

            writeStreamToFile(records, path0);

            Butterworth butterworth = new Butterworth();

            butterworth.highPass(4, 10, 0.01);


            List<double[]> filteredSignal = filterSignal(records, windowSize);

            double meanValue = getMeanValue(filteredSignal);

            System.out.printf("Mean Value : %f", meanValue);

            List<double[]> subtractedSignal = filteredSignal.stream()
                    .map(row -> new double[]{row[0], row[1] - meanValue})
                    .collect(Collectors.toList());

            writeStreamToFile(subtractedSignal, path1);

            List<double[]> integratedSignal = integrateSignal(subtractedSignal);

            List<double[]> filteredIntSignal  = integratedSignal.stream()
                    .map(r -> new double[]{r[0], butterworth.filter(r[1])})
                    .collect(Collectors.toList());

//            List<double[]> filteredIntSignal = filterSignal(integratedSignal, windowSize);

            writeStreamToFile(filteredIntSignal, path2);

            List<double[]> integratedSignal2 = integrateSignal(filteredIntSignal);

            writeStreamToFile(integratedSignal2, path3);

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
