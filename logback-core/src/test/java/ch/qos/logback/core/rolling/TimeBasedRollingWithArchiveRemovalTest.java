package ch.qos.logback.core.rolling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.layout.EchoLayout;
import ch.qos.logback.core.testUtil.RandomUtil;
import ch.qos.logback.core.util.CoreTestConstants;

public class TimeBasedRollingWithArchiveRemovalTest {

  Context context = new ContextBase();
  EchoLayout<Object> layout = new EchoLayout<Object>();

  static final String MONTHLY_DATE_PATTERN = "yyyy-MM";
  static final String MONTHLY_CROLOLOG_DATE_PATTERN = "yyyy/MM";

  static final String DAILY_DATE_PATTERN = "yyyy-MM-dd";
  static final String DAILY_CROLOLOG_DATE_PATTERN = "yyyy/MM/dd";

  static final long MILLIS_IN_MINUTE = 60 * 1000;
  static final long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;
  static final long MILLIS_IN_DAY = 24 * MILLIS_IN_HOUR;
  static final long MILLIS_IN_MONTH = 30 * MILLIS_IN_DAY;

  int diff = RandomUtil.getPositiveInt();
  protected String randomOutputDir = CoreTestConstants.OUTPUT_DIR_PREFIX + diff
      + "/";
  int slashCount;

  // by default tbfnatp is an instance of
  // DefaultTimeBasedFileNamingAndTriggeringPolicy
  TimeBasedFileNamingAndTriggeringPolicy<Object> tbfnatp = new DefaultTimeBasedFileNamingAndTriggeringPolicy<Object>();

  @Before
  public void setUp() throws Exception {
    context.setName("test");
  }

  @After
  public void tearDown() throws Exception {
  }

  int computeSlashCount(String datePattern) {
    int fromIndex = 0;
    int count = 0;
    while (true) {
      int i = datePattern.indexOf('/', fromIndex);
      if (i == -1) {
        break;
      } else {
        count++;
        fromIndex = i + 1;
        if (fromIndex >= datePattern.length()) {
          break;
        }
      }
    }
    return count;
  }

  @Test
  public void montlyRollover() throws Exception {
    slashCount = computeSlashCount(MONTHLY_DATE_PATTERN);
    // large maxPeriod, a 3 times as many number of periods to simulate
    doRollover(randomOutputDir + "clean-%d{" + MONTHLY_DATE_PATTERN + "}.txt",
        MILLIS_IN_MONTH, 20, 20 * 3);
    check(expectedCountWithoutDirs(20));
  }

  @Test
  public void montlyRolloverOverManyPeriods() throws Exception {
    System.out.println(randomOutputDir);
    // small maxHistory, many periods
    slashCount = computeSlashCount(MONTHLY_CROLOLOG_DATE_PATTERN);
    doRollover(randomOutputDir + "/%d{" + MONTHLY_CROLOLOG_DATE_PATTERN
        + "}/clean.txt.zip", MILLIS_IN_MONTH, 2, 40);
    check(expectedCountWithDirs(2));
  }

  @Test
  public void dailyRollover() throws Exception {
    slashCount = computeSlashCount(DAILY_DATE_PATTERN);
    doRollover(
        randomOutputDir + "clean-%d{" + DAILY_DATE_PATTERN + "}.txt.zip",
        MILLIS_IN_DAY, 5, 5 * 3);
    check(expectedCountWithoutDirs(5));
  }

  @Test
  public void dailyCronologRollover() throws Exception {
    slashCount = computeSlashCount(DAILY_CROLOLOG_DATE_PATTERN);
    doRollover(randomOutputDir + "/%d{" + DAILY_CROLOLOG_DATE_PATTERN
        + "}/clean.txt.zip", MILLIS_IN_DAY, 8, 8 * 3);
    check(expectedCountWithDirs(8));
  }

  @Test
  public void dailySizeBasedRollover() throws Exception {
    SizeAndTimeBasedFNATP<Object> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<Object>();
    sizeAndTimeBasedFNATP.setMaxFileSize("10");
    tbfnatp = sizeAndTimeBasedFNATP;

    slashCount = computeSlashCount(DAILY_DATE_PATTERN);
    doRollover(
        randomOutputDir + "/%d{" + DAILY_DATE_PATTERN + "}-clean.%i.zip",
        MILLIS_IN_DAY, 5, 5 * 4);
    checkPatternCompliance(5 + 1 + slashCount,
        "\\d{4}-\\d{2}-\\d{2}-clean.(\\d).zip");
  }

  @Test
  public void dailyChronologSizeBasedRollover() throws Exception {
    SizeAndTimeBasedFNATP<Object> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<Object>();
    sizeAndTimeBasedFNATP.setMaxFileSize("10");
    tbfnatp = sizeAndTimeBasedFNATP;

    slashCount = 1;
    doRollover(
        randomOutputDir + "/%d{" + DAILY_DATE_PATTERN + "}/clean.%i.zip",
        MILLIS_IN_DAY, 5, 5 * 4);
    checkDirPatternCompliance(6);
  }

  void doRollover(String fileNamePattern, long periodDurationInMillis,
      int maxHistory, int simulatedNumberOfPeriods) throws Exception {
    long currentTime = System.currentTimeMillis();

    RollingFileAppender<Object> rfa = new RollingFileAppender<Object>();
    rfa.setContext(context);
    rfa.setLayout(layout);
    // rfa.setFile(Constants.OUTPUT_DIR_PREFIX + "clean.txt");
    TimeBasedRollingPolicy<Object> tbrp = new TimeBasedRollingPolicy<Object>();
    tbrp.setContext(context);
    tbrp.setFileNamePattern(fileNamePattern);

    tbrp.setMaxHistory(maxHistory);
    tbrp.setParent(rfa);
    tbrp.timeBasedTriggering = tbfnatp;
    tbrp.timeBasedTriggering.setCurrentTime(currentTime);
    tbrp.start();
    rfa.setRollingPolicy(tbrp);
    rfa.start();

    int ticksPerPeriod = 64;
    long runLength = simulatedNumberOfPeriods * ticksPerPeriod;

    for (long i = 0; i < runLength; i++) {
      rfa
          .doAppend("Hello ----------------------------------------------------------"
              + i);
      tbrp.timeBasedTriggering.setCurrentTime(addTime(tbrp.timeBasedTriggering
          .getCurrentTime(), periodDurationInMillis / ticksPerPeriod));

      if (tbrp.future != null) {
        tbrp.future.get(200, TimeUnit.MILLISECONDS);
      }
    }
    rfa.stop();
  }

  void findAllInFolderRecursivelyByStringContains(File dir, List<File> fileList,
      final String pattern) {
    if (dir.isDirectory()) {
      File[] match = dir.listFiles(new FileFilter() {
        public boolean accept(File f) {
          return (f.isDirectory() || f.getName().contains(pattern));
        }
      });
      for (File f : match) {
        fileList.add(f);
        if (f.isDirectory()) {
          findAllInFolderRecursivelyByStringContains(f, fileList, pattern);
        }
      }
    }
  }

  void findFilesInFolderRecursivelyByPatterMatch(File dir, List<File> fileList,
      final String pattern) {
    if (dir.isDirectory()) {
      File[] match = dir.listFiles(new FileFilter() {
        public boolean accept(File f) {
          return (f.isDirectory() || f.getName().matches(pattern));
        }
      });
      for (File f : match) {
        if (f.isDirectory()) {
          findFilesInFolderRecursivelyByPatterMatch(f, fileList, pattern);
        } else {
          fileList.add(f);
        }
      }
    }
  }

  void findFoldersInFolderRecursively(File dir, List<File> fileList) {
    if (dir.isDirectory()) {
      File[] match = dir.listFiles(new FileFilter() {
        public boolean accept(File f) {
          return f.isDirectory();
        }
      });
      for (File f : match) {
        fileList.add(f);
        findFoldersInFolderRecursively(f, fileList);
      }
    }
  }

  int expectedCountWithoutDirs(int maxHistory) {
    // maxHistory plus the currently active file
    return maxHistory + 1;
  }

  int expectedCountWithDirs(int maxHistory) {
    // each slash adds a new directory
    // + one file and one directory per archived log file
    return (maxHistory + 1) * 2 + slashCount;
  }

  void check(int expectedCount) {
    File dir = new File(randomOutputDir);
    List<File> fileList = new ArrayList<File>();
    findAllInFolderRecursivelyByStringContains(dir, fileList, "clean");
    assertEquals(expectedCount, fileList.size());
  }

  void checkPatternCompliance(int expectedClassCount, String regex) {
    File dir = new File(randomOutputDir);
    List<File> fileList = new ArrayList<File>();
    findFilesInFolderRecursivelyByPatterMatch(dir, fileList, regex);
    System.out.println("regex="+regex);
    System.out.println("fileList="+fileList);
    Set<String> set = groupByClass(fileList, regex);
    assertEquals(expectedClassCount, set.size());
  }

  void checkDirPatternCompliance(int expectedClassCount) {
    File dir = new File(randomOutputDir);
    List<File> fileList = new ArrayList<File>();
    findFoldersInFolderRecursively(dir, fileList);
    for(File f: fileList) {
      assertTrue(f.list().length >= 1);
    }
    assertEquals(expectedClassCount, fileList.size());
  }

  
  Set<String> groupByClass(List<File> fileList, String regex) {
    Pattern p = Pattern.compile(regex);
    Set<String> set = new HashSet<String>();
    for (File f : fileList) {
      String n = f.getName();
      Matcher m = p.matcher(n);
      m.matches();
      int begin = m.start(1);
      int end = m.end(1);
      set.add(n.substring(0, begin) + n.substring(end));
    }
    return set;
  }

  static long addTime(long currentTime, long timeToWait) {
    return currentTime + timeToWait;
  }

}