@playwright @ADO#notification @ADO#websocket
Feature: Websocket Notification Delivery E2E
  As a checker
  I want to observe maker booking events in near real time
  So that I can react without manual refresh

  Background:
    Given checker notification counter is captured

  @ADO#positive @ADO#smoke
  Scenario: [TC-WEBSOCKET-NOTIFICATION-UI-001] Checker notification updates after maker books one trade
    When maker books one trade via api
    Then checker notification count updates and content contains booked trade id
