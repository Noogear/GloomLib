package gloomlib.gui.animation;

import java.util.List;
import java.util.function.Function;

@FunctionalInterface
public interface Animation<T> {

    static <T> Animation<T> ofFrames(List<T> frames, int ticksPerFrame) {
        return tick -> {
            int totalFrames = frames.size();
            int currentFrameIndex = (int) ((tick / ticksPerFrame) % totalFrames);
            return frames.get(currentFrameIndex);
        };
    }

    static <T> Animation<T> interpolate(double start, double end, int durationTicks, Function<Double, T> interpolator) {
        return tick -> {
            double progress = Math.min(1.0, (double) tick / durationTicks);
            double value = start + (end - start) * progress;
            return interpolator.apply(value);
        };
    }

    T getFrame(long tick);
}