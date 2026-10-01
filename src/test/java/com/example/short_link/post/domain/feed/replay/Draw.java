package com.example.short_link.post.domain.feed.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// StrictMath keeps every draw bit-identical across JVMs, which the metric snapshot depends on.
final class Draw {

  private Draw() {}

  static int poisson(Random random, double lambda) {
    double limit = StrictMath.exp(-lambda);
    double product = random.nextDouble();
    int count = 0;
    while (product > limit) {
      product *= random.nextDouble();
      count++;
    }
    return count;
  }

  static double logNormal(Random random, double mu, double sigma) {
    return StrictMath.exp(mu + sigma * random.nextGaussian());
  }

  static double pareto(Random random, double alpha) {
    return 1.0 / StrictMath.pow(1.0 - random.nextDouble(), 1.0 / alpha);
  }

  static double[] dirichlet(Random random, double alpha, int size) {
    double[] values = new double[size];
    double sum = 0;
    for (int i = 0; i < size; i++) {
      values[i] = gamma(random, alpha);
      sum += values[i];
    }
    for (int i = 0; i < size; i++) {
      values[i] /= sum;
    }
    return values;
  }

  static int weighted(Random random, double[] weights) {
    double total = 0;
    for (double w : weights) {
      total += w;
    }
    double target = random.nextDouble() * total;
    double running = 0;
    for (int i = 0; i < weights.length; i++) {
      running += weights[i];
      if (target < running) {
        return i;
      }
    }
    return weights.length - 1;
  }

  static <T> List<T> sample(Random random, List<T> from, int count) {
    List<T> copy = new ArrayList<>(from);
    Collections.shuffle(copy, random);
    return List.copyOf(copy.subList(0, Math.min(count, copy.size())));
  }

  private static double gamma(Random random, double shape) {
    if (shape < 1) {
      return gamma(random, shape + 1) * StrictMath.pow(random.nextDouble(), 1.0 / shape);
    }
    double d = shape - 1.0 / 3;
    double c = 1.0 / StrictMath.sqrt(9 * d);
    while (true) {
      double x = random.nextGaussian();
      double v = 1 + c * x;
      if (v <= 0) {
        continue;
      }
      v = v * v * v;
      double u = random.nextDouble();
      if (StrictMath.log(u) < 0.5 * x * x + d - d * v + d * StrictMath.log(v)) {
        return d * v;
      }
    }
  }
}
