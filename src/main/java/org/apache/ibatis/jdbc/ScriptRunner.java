/*
 *    Copyright 2009-2012 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.jdbc;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author Clinton Begin
 */
/**
 * 脚本运行器,可以运行SQL脚本，如建表，插入数据，作为单元测试的前期准备
 * 这个类其实可以被所有项目的单元测试作为工具所利用
 */
public class ScriptRunner {

  private static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");

  private static final String DEFAULT_DELIMITER = ";";
  /**
   *  数据库连接
   * */
  private Connection connection;

  /**
   *  出现异常是否停止执行
   * */
  private boolean stopOnError;

  //是否自动提交
  private boolean autoCommit;

  /**
   *  当前文件中的脚本是否是一个全脚本，意思是只有一条sql语句
   * */
  private boolean sendFullScript;

  /**
   *  是否移除制表符
   * */
  private boolean removeCRs;


  private boolean escapeProcessing = true;

  private PrintWriter logWriter = new PrintWriter(System.out);
  private PrintWriter errorLogWriter = new PrintWriter(System.err);

  private String delimiter = DEFAULT_DELIMITER;
  private boolean fullLineDelimiter = false;

  public ScriptRunner(Connection connection) {
    this.connection = connection;
  }

  public void setStopOnError(boolean stopOnError) {
    this.stopOnError = stopOnError;
  }

  public void setAutoCommit(boolean autoCommit) {
    this.autoCommit = autoCommit;
  }

  public void setSendFullScript(boolean sendFullScript) {
    this.sendFullScript = sendFullScript;
  }

  public void setRemoveCRs(boolean removeCRs) {
    this.removeCRs = removeCRs;
  }

  /**
   * @since 3.1.1
   */
  public void setEscapeProcessing(boolean escapeProcessing) {
    this.escapeProcessing = escapeProcessing;
  }

  public void setLogWriter(PrintWriter logWriter) {
    this.logWriter = logWriter;
  }

  public void setErrorLogWriter(PrintWriter errorLogWriter) {
    this.errorLogWriter = errorLogWriter;
  }

  public void setDelimiter(String delimiter) {
    this.delimiter = delimiter;
  }

  public void setFullLineDelimiter(boolean fullLineDelimiter) {
    this.fullLineDelimiter = fullLineDelimiter;
  }

  public void runScript(Reader reader) {
    //1. 设置自动提交
    setAutoCommit();

    try {
      //2. 根据语句类型执行脚本
      if (sendFullScript) {
        executeFullScript(reader);
      } else {
        executeLineByLine(reader);
      }
    } finally {
      //3. 回滚
      rollbackConnection();
    }
  }

  /**
   *  流中的的字符串为一个sql语句
   * */
  private void executeFullScript(Reader reader) {
    StringBuilder script = new StringBuilder();
    try {
      BufferedReader lineReader = new BufferedReader(reader);
      // 1. 通过流构建sql语句
      String line;
      while ((line = lineReader.readLine()) != null) {
        script.append(line);
        script.append(LINE_SEPARATOR);
      }
      //2. 执行sql语句
      executeStatement(script.toString());
      commitConnection();
    } catch (Exception e) {
      String message = "Error executing: " + script + ".  Cause: " + e;
      printlnError(message);
      throw new RuntimeSqlException(message, e);
    }
  }

  /**
   * 文件中为多个sql语句，并通过分隔符分割， 然后逐个执行，最后提交
   * */
  private void executeLineByLine(Reader reader) {
    StringBuilder command = new StringBuilder();
    try {
      BufferedReader lineReader = new BufferedReader(reader);
      String line;
      //1. 逐个读取并执行
      while ((line = lineReader.readLine()) != null) {
        command = handleLine(command, line);
      }
      //2. 提交执行结果
      commitConnection();
      //3. 如果执行完毕是comand的长度还是大于0，说明最后一个sql命令并没有结束符，抛出异常
      checkForMissingLineTerminator(command);
    } catch (Exception e) {
      String message = "Error executing: " + command + ".  Cause: " + e;
      printlnError(message);
      throw new RuntimeSqlException(message, e);
    }
  }

  public void closeConnection() {
    try {
      connection.close();
    } catch (Exception e) {
      // ignore
    }
  }

  private void setAutoCommit() {
    try {
      if (autoCommit != connection.getAutoCommit()) {
        connection.setAutoCommit(autoCommit);
      }
    } catch (Throwable t) {
      throw new RuntimeSqlException("Could not set AutoCommit to " + autoCommit + ". Cause: " + t, t);
    }
  }

  /**
   * 提交
   * */
  private void commitConnection() {
    try {
      if (!connection.getAutoCommit()) {
        connection.commit();
      }
    } catch (Throwable t) {
      throw new RuntimeSqlException("Could not commit transaction. Cause: " + t, t);
    }
  }

  /**
   *  回滚
   * */
  private void rollbackConnection() {
    try {
      if (!connection.getAutoCommit()) {
        connection.rollback();
      }
    } catch (Throwable t) {
      // ignore
    }
  }

  private void checkForMissingLineTerminator(StringBuilder command) {
    if (command != null && command.toString().trim().length() > 0) {
      throw new RuntimeSqlException("Line missing end-of-line terminator (" + delimiter + ") => " + command);
    }
  }

  /**
   *
   * */
  private StringBuilder handleLine(StringBuilder command, String line) throws SQLException, UnsupportedEncodingException {
    String trimmedLine = line.trim();
    //1. 如果改行为注释,打印注释内容
    if (lineIsComment(trimmedLine)) {
      println(trimmedLine);
    //2. 如果遇到了分割符 ";"，则表示当前的命令已经为一个完整的sql命令，开始执行
    } else if (commandReadyToExecute(trimmedLine)) {
      command.append(line.substring(0, line.lastIndexOf(delimiter)));
      command.append(LINE_SEPARATOR);
      println(command);
      executeStatement(command.toString());
      command.setLength(0);
    //3. 如果还没遇到分隔符，则表示当前以读取的内容并不是一个完整的sql命令，则接着读取追加
    } else if (trimmedLine.length() > 0) {
      command.append(line);
      command.append(LINE_SEPARATOR);
    }
    return command;
  }

  /**
   *  判断该行是否是注释
   * */
  private boolean lineIsComment(String trimmedLine) {
    return trimmedLine.startsWith("//") || trimmedLine.startsWith("--");
  }

  private boolean commandReadyToExecute(String trimmedLine) {
    // issue #561 remove anything after the delimiter
    return !fullLineDelimiter && trimmedLine.contains(delimiter) || fullLineDelimiter && trimmedLine.equals(delimiter);
  }

  /**
   *  通过原生的jdbc连接执行sqL命令然后打印结果
   * */
  private void executeStatement(String command) throws SQLException {
    boolean hasResults = false;
    Statement statement = connection.createStatement();
    statement.setEscapeProcessing(escapeProcessing);
    String sql = command;
    if (removeCRs) {
      sql = sql.replaceAll("\r\n", "\n");
    }
    if (stopOnError) {
      hasResults = statement.execute(sql);
    } else {
      try {
        hasResults = statement.execute(sql);
      } catch (SQLException e) {
        String message = "Error executing: " + command + ".  Cause: " + e;
        printlnError(message);
      }
    }
    printResults(statement, hasResults);
    try {
      statement.close();
    } catch (Exception e) {
      // Ignore to workaround a bug in some connection pools
    }
  }

  /**
   *  打印sql语句执行结果ResultSet
   * */
  private void printResults(Statement statement, boolean hasResults) {
    try {
      if (hasResults) {
        ResultSet rs = statement.getResultSet();
        if (rs != null) {
          ResultSetMetaData md = rs.getMetaData();
          int cols = md.getColumnCount();
          for (int i = 0; i < cols; i++) {
            String name = md.getColumnLabel(i + 1);
            print(name + "\t");
          }
          println("");
          while (rs.next()) {
            for (int i = 0; i < cols; i++) {
              String value = rs.getString(i + 1);
              print(value + "\t");
            }
            println("");
          }
        }
      }
    } catch (SQLException e) {
      printlnError("Error printing results: " + e.getMessage());
    }
  }
  //-------------------------------------向控制台打印-----------------------------------------------
  private void print(Object o) {
    if (logWriter != null) {
      logWriter.print(o);
      logWriter.flush();
    }
  }

  private void println(Object o) {
    if (logWriter != null) {
      logWriter.println(o);
      logWriter.flush();
    }
  }
  private void printlnError(Object o) {
    if (errorLogWriter != null) {
      errorLogWriter.println(o);
      errorLogWriter.flush();
    }
  }

}
