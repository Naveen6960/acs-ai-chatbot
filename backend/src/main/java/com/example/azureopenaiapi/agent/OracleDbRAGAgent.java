package com.example.azureopenaiapi.agent;

import java.sql.Connection;

public class OracleDbRAGAgent implements Agent {
    @org.springframework.beans.factory.annotation.Value("${oracle.db.url}")
    private String dbUrl;
    @org.springframework.beans.factory.annotation.Value("${oracle.db.username}")
    private String dbUsername;
    @org.springframework.beans.factory.annotation.Value("${oracle.db.password}")
    private String dbPassword;
    private Connection dbConnection;

    public OracleDbRAGAgent() {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            this.dbConnection = java.sql.DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Oracle DB: " + e.getMessage(), e);
        }
    }

    @Override
    public String process(String userInput, String employeeId) {
        try {
            java.sql.DatabaseMetaData metaData = dbConnection.getMetaData();
            java.sql.ResultSet tables = metaData.getTables(null, dbUsername.toUpperCase(), "%", new String[]{"TABLE"});
            StringBuilder tableInfo = new StringBuilder("Available tables:\n");
            java.util.List<String> tableNames = new java.util.ArrayList<>();
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                tableNames.add(tableName);
                tableInfo.append(tableName).append("\n");
            }
            tables.close();

            String tablePrompt = "User question: " + userInput + "\n" + tableInfo.toString() + "\nWhich table(s) should be used?";
            String chosenTable = callLLM(tablePrompt);

            java.sql.ResultSet columns = metaData.getColumns(null, dbUsername.toUpperCase(), chosenTable.trim(), "%");
            StringBuilder schemaInfo = new StringBuilder("Schema for table " + chosenTable + ":\n");
            while (columns.next()) {
                schemaInfo.append(columns.getString("COLUMN_NAME")).append(" (")
                    .append(columns.getString("TYPE_NAME")).append(")\n");
            }
            columns.close();

            String queryPrompt = "User question: " + userInput + "\nTable schema: " + schemaInfo.toString() + "\nBuild a valid SQL query.";
            String sqlQuery = callLLM(queryPrompt);

            String confirmPrompt = "Is this SQL query syntactically correct for Oracle?\n" + sqlQuery;
            String confirmResult = callLLM(confirmPrompt);
            if (!confirmResult.toLowerCase().contains("yes")) {
                return "LLM could not confirm query syntax: " + confirmResult;
            }

            java.sql.Statement stmt = dbConnection.createStatement();
            java.sql.ResultSet rs = stmt.executeQuery(sqlQuery);
            StringBuilder queryData = new StringBuilder("Query results:\n");
            java.sql.ResultSetMetaData rsMeta = rs.getMetaData();
            int colCount = rsMeta.getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= colCount; i++) {
                    queryData.append(rsMeta.getColumnName(i)).append(": ").append(rs.getString(i)).append("; ");
                }
                queryData.append("\n");
            }
            rs.close();
            stmt.close();

            String analyzePrompt = "Analyze these query results for the user's question: " + userInput + "\n" + queryData.toString();
            String analysis = callLLM(analyzePrompt);
            return analysis;
        } catch (Exception e) {
            return "Error in OracleDbRAGAgent: " + e.getMessage();
        }
    }

    private String callLLM(String prompt) {
        return prompt;
    }
}