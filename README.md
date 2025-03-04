# Stealth Agent

# Overview

StealthAgent is an **AI-driven pathfinding system** implemented in **Java** that utilizes the A* Search Algorithm to navigate an agent through a **dynamic environment**. The agent's goal is to steal an arbituary amount of gold resources, attack the enemy agents town hall, and strategically safely return to the designated safe zone while avoiding obstacles and detection.

# Key Features

- 🗺️ **Grid-Based Map Representation**: The environment is represented as a grid where the agent moves strategically to complete objectives while avoiding detection.

- 🧠 **A* Search Algorithm**: The algorithm calculates the optimal path using the cost function:

      f(n) = g(n) + h(n)

      g(n): Cost from start node to the current node

      h(n): Estimated cost from the current node to the goal (heuristic)

- 💰 **Gold Collection & Attack Strategy**: The agent locates and collects gold while plotting an attack on the town hall.

- 🏠 **Safe Zone Return**: Once objectives are complete, the agent must safely return to a predefined safe zone.
