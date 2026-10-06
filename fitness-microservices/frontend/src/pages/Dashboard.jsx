import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import apiClient from "../api/client";
import ActivityItem from "./ActivityItem";

export default function Dashboard() {
  const { userId, logout } = useAuth();
  const [activities, setActivities] = useState([]);
  const [type, setType] = useState("RUNNING");
  const [duration, setDuration] = useState("");
  const [calories, setCalories] = useState("");
  const [error, setError] = useState("");

  async function loadActivities() {
    const response = await apiClient.get(`/api/activities/user/${userId}`);
    setActivities(response.data);
  }

  useEffect(() => {
    loadActivities();
  }, [userId]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      await apiClient.post("/api/activities", {
        userId,
        type,
        durationInMinutes: Number(duration),
        caloriesBurned: Number(calories),
      });
      setDuration("");
      setCalories("");
      loadActivities();
    } catch (err) {
      setError("Could not log activity");
    }
  }

  return (
    <div>
      <h1>Dashboard</h1>
      <button onClick={logout}>Logout</button>

      <form onSubmit={handleSubmit}>
        <select value={type} onChange={(e) => setType(e.target.value)}>
          <option value="RUNNING">Running</option>
          <option value="CYCLING">Cycling</option>
          <option value="WALKING">Walking</option>
        </select>
        <input
          placeholder="Duration (minutes)"
          type="number"
          value={duration}
          onChange={(e) => setDuration(e.target.value)}
        />
        <input
          placeholder="Calories burned"
          type="number"
          value={calories}
          onChange={(e) => setCalories(e.target.value)}
        />
        <button type="submit">Log activity</button>
      </form>
      {error && <p>{error}</p>}

      <h2>Your Activities</h2>
      <ul>
        {activities.map((activity) => (
          <ActivityItem key={activity.id} activity={activity} />
        ))}
      </ul>
    </div>
  );
}
