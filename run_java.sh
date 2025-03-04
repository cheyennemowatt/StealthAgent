#!/bin/bash

files=(
    "data/pas/stealth/OneUnitSmallMaze.xml"
    "data/pas/stealth/TwoUnitSmallMaze.xml"
    "data/pas/stealth/BigMaze.xml"
)

total_occurrences=0
total_runs=$((300 * ${#files[@]}))  # Total number of runs

# Function to handle quitting the script
trap "echo 'Script interrupted. Exiting...'; exit 1" SIGINT

# Run each Java command 300 times
for file in "${files[@]}"; do
    file_occurrences=0  # Reset occurrences for the current file

    for i in {1..300}; do
        echo "Running iteration $i for $file ($i/300)"

        # Check if user wants to quit
        read -t 1 -n 1 key
        if [[ $key == "q" ]]; then
            echo "Quitting script early."
            exit 0
        fi

        # Run the Java command and capture the output
        output=$(java -cp "./lib/*:." edu.cwru.sepia.Main2 "$file")
        
        # Count occurrences of the win message
        occurrences=$(echo "$output" | grep -o "The enemy was destroyed, you win!" | wc -l)

        # Increment counters
        total_occurrences=$((total_occurrences + occurrences)) 
        file_occurrences=$((file_occurrences + occurrences))
    done

    # Calculate win percentage for the current file
    win_percentage=$(echo "scale=2; ($file_occurrences / 300) * 100" | bc)

    # Display results for this file
    echo "-------------------------------------"
    echo "Results for $file:"
    echo "  Total wins: $file_occurrences/300"
    echo "  Win percentage: $win_percentage%"
    echo "-------------------------------------"
done

# Calculate and display the overall win percentage
overall_win_percentage=$(echo "scale=2; ($total_occurrences / $total_runs) * 100" | bc)
echo "====================================="
echo "Overall win percentage across all files: $overall_win_percentage%"
echo "Script finished."
