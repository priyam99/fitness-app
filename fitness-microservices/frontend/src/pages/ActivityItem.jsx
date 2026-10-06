import { useEffect, useState } from "react";
import apiClient from "../api/client";

export default function ActivityItem({ activity }) {
  const [recommendation, setRecommendation] = useState(null);

  useEffect(() => {
    let cancelled = false;

    apiClient
      .get(`/api/recommendations/activity/${activity.id}`)
      .then((response) => {
        if (!cancelled) {
          setRecommendation(response.data.recommendation);
        }
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [activity.id]);

  return (
    <li>
      {activity.type} - {activity.durationInMinutes} min - {activity.caloriesBurned} cal
      {recommendation && <p>AI tip: {recommendation}</p>}
    </li>
  );
}
