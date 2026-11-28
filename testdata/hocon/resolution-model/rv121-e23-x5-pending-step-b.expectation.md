# rv121-e23-x5-pending-step-b — expectation from the reference model, not from Lightbend

kind: pending-step-b
e23-final: {"a": {"y": 1}, "b": 1}
interim: ERR
lightbend-1.4.9: ERR

E23 "Why we diverge" example: rule 1 drops `${?b}`, so `a = {y: 1}` and `b = 1`. In this step (interim cycle rule, resolution-model §6.3) the innermost frame is the required `${a.y}`: an error, as in Lightbend.

See docs/resolution-model.md (§11, §12) and docs/fixture-conventions.md "resolution-model/".
