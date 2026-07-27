from typing import List, Dict, Any, Set, Tuple

def calculate_binary_metrics(tp: int, fp: int, fn: int, tn: int) -> Dict[str, float]:
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
    accuracy = (tp + tn) / (tp + fp + fn + tn) if (tp + fp + fn + tn) > 0 else 0.0
    return {
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "accuracy": accuracy
    }

class EvaluationReport:
    def __init__(self):
        self.metrics: Dict[str, Any] = {}

    def evaluate_role_classification(
        self,
        predictions: List[str],
        ground_truth: List[str]
    ) -> Dict[str, Any]:
        """
        Calculate metrics for multi-class discourse role classification.
        """
        classes = sorted(list(set(predictions + ground_truth)))
        confusion_matrix = {c_gt: {c_pred: 0 for c_pred in classes} for c_gt in classes}

        for gt, pred in zip(ground_truth, predictions):
            confusion_matrix[gt][pred] += 1

        per_class_metrics = {}
        for c in classes:
            tp = confusion_matrix[c][c]
            fp = sum(confusion_matrix[other][c] for other in classes if other != c)
            fn = sum(confusion_matrix[c][other] for other in classes if other != c)
            tn = sum(sum(confusion_matrix[o1][o2] for o2 in classes if o2 != c) for o1 in classes if o1 != c)

            per_class_metrics[c] = calculate_binary_metrics(tp, fp, fn, tn)

        self.metrics["discourse_roles"] = {
            "confusion_matrix": confusion_matrix,
            "per_class": per_class_metrics
        }
        return self.metrics["discourse_roles"]

    def evaluate_requirement_candidacy(
        self,
        predicted_candidates: Set[str],
        ground_truth_candidates: Set[str],
        all_item_ids: Set[str]
    ) -> Dict[str, float]:
        """
        Calculate precision/recall for requirement candidacy.
        """
        tp = len(predicted_candidates.intersection(ground_truth_candidates))
        fp = len(predicted_candidates - ground_truth_candidates)
        fn = len(ground_truth_candidates - predicted_candidates)
        tn = len(all_item_ids - predicted_candidates - ground_truth_candidates)

        metrics = calculate_binary_metrics(tp, fp, fn, tn)
        self.metrics["requirement_candidacy"] = metrics
        return metrics
