package link.srrrg.statistics;

import java.time.Instant;
import java.time.LocalDate;

record StatisticsPeriod(LocalDate from, LocalDate to, Instant start, Instant end, Instant previousStart) { }
