package edu.nyu.cs.foodie.Recommender;

import java.sql.*;
import java.util.*;

import edu.nyu.cs.foodie.GeneticAlgorithm.FitnessCalc;
import edu.nyu.cs.foodie.GeneticAlgorithm.GA;
import edu.nyu.cs.foodie.GeneticAlgorithm.Individual;
import edu.nyu.cs.foodie.GeneticAlgorithm.Population;
import edu.nyu.cs.foodie.util.ValueComparator;

public class FilterGA {

  Connection c;
  Set<String> candidates = new HashSet<>();
  Set<String> friends = new HashSet<>();
  Map<String, Set<String>> graph = new HashMap<>();
  Map<String, Integer> scoreMap = new HashMap<>();
  List<Map.Entry<String, Integer>> recommendList = new ArrayList<>();

  public FilterGA(Connection c) {
    this.c = c;
  }

  public Set<String> recommend(String userID) {
    if (userID == null || userID.isEmpty()) {
      throw new IllegalArgumentException();
    }

    graph.put(userID, new HashSet<>());

    filtering(userID);
    System.out.println("friends size: " + friends.size());

    candidates.removeAll(friends);

    System.out.println("candidate size before GA: " + candidates.size());
    if (candidates.size() > 1000) {
      Random rand = new Random();
      List<String> tmp = new ArrayList<>(candidates);
      candidates.clear();
      for (int i = 0; i < 1000; i++) {
        int index = rand.nextInt(tmp.size());
        while (candidates.contains(tmp.get(index))) {
          index = rand.nextInt(tmp.size());
        }
        candidates.add(tmp.get(index));
      }
    }

    ordering();

    System.out.println("recommendList size after GA: " + recommendList.size());

    Set<String> result = new HashSet<>();
    for (Map.Entry<String, Integer> entry : recommendList) {
      result.add(entry.getKey());
    }

    System.out.println("result size in FilterGA: " + result.size());

    return result;
  }

  private void ordering() {
    try {
      for (String candidate : candidates) {
        Set<String> candidateFriend = new HashSet<>();
        PreparedStatement pstmt = c.prepareStatement("select toid from friends where fromid = ?");
        pstmt.setString(1, candidate);
        ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
          if (!graph.containsKey(rs.getString(1))) {
            graph.put(rs.getString(1), new HashSet<>());
          }
          graph.get(candidate).add(rs.getString(1));
          graph.get(rs.getString(1)).add(candidate);
          candidateFriend.add(rs.getString(1));
        }

        Set<String> intersection = new HashSet<>(friends);
        intersection.retainAll(candidateFriend);

        Set<String> union = new HashSet<>(friends);
        union.addAll(candidateFriend);

        int firstIndex = intersection.size();
        int secondIndex = getDensity(intersection);
        int thirdIndex = getDensity(union);

        FitnessCalc.set(firstIndex, secondIndex, thirdIndex);
        Population population = new Population(200, true);
        Population resultPopulation = GA.evolvePopulation(population);
        Individual bestIndividual = resultPopulation.getFittest();
        int fitness = bestIndividual.getFitness();
        scoreMap.put(candidate, fitness);
      }

      recommendList.addAll(scoreMap.entrySet());
      Collections.sort(recommendList, new ValueComparator<>());

    } catch (SQLException e) {
      e.printStackTrace();
      System.err.println("[Error]: Fail to fetch friends from DB.");
      System.exit(1);
    }
  }

  private int getDensity(Set<String> set) {
    int links = 0;
    for (String v : set) {
      for (String adj : graph.get(v)) {
        if (set.contains(adj)) {
          links++;
        }
      }
    }
    return links / 2;
  }

  private void filtering(String userID) {
    try {
      PreparedStatement fstmt = c.prepareStatement("select toid from friends where fromid = ?");
      PreparedStatement fofStmt = c.prepareStatement("select toid from friends where fromid = ?");
      fstmt.setString(1, userID);
      ResultSet rs = fstmt.executeQuery();
      ResultSet fofRS;
      while (rs.next()) {
        String friend = rs.getString(1);
        graph.get(userID).add(friend);
        if (!graph.containsKey(friend)) {
          graph.put(friend, new HashSet<>());
        }
        graph.get(friend).add(userID);
        friends.add(friend);
        fofStmt.setString(1, friend);
        fofRS = fofStmt.executeQuery();
        while (fofRS.next()) {
          String fof = fofRS.getString(1);
          graph.get(friend).add(fof);
          if (!graph.containsKey(fof)) {
            graph.put(fof, new HashSet<>());
          }
          graph.get(fof).add(friend);
          candidates.add(fof);
        }
        fofRS.close();
      }
      rs.close();
      fstmt.close();
    } catch (SQLException e) {
      e.printStackTrace();
      System.err.println("[Error]: Fail to fetch user friends in DB.");
      System.exit(1);
    }
  }

}
