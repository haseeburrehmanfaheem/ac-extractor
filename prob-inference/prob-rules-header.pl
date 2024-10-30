%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%Basic Fact Rules
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

% Basic facts from API AC extraction
0.95 :: api_ac(Api, Orig, system) :- fw_ac(Api, Orig, system, Id).
0.95 :: api_ac(Api, Orig, dangerous) :- fw_ac(Api, Orig, dangerous, Id).
0.8 :: api_ac(Api, Orig, normal) :- fw_ac(Api, Orig, normal, Id).
0.1 :: api_ac(Api, Orig, no_ac) :- fw_ac(Api, Orig, no_ac, Id).

% Basic facts from field annotations
0.95 :: field_op_ac(Field, FOrig, Orig, set, Gran, Protection) :- api_ac(Api, Orig, Protection), api_performs(Orig, Field, FOrig, set, Gran, Id).
0.95 :: field_op_ac(Field, FOrig, Orig, get, Gran, Protection) :- api_ac(Api, Orig, Protection), api_performs(Orig, Field, FOrig, get, Gran, Id).
0.95 :: field_op_ac(Field, FOrig, Orig, add, Gran, Protection) :- api_ac(Api, Orig, Protection), api_performs(Orig, Field, FOrig, add, Gran, Id).
0.95 :: field_op_ac(Field, FOrig, Orig, remove, Gran, Protection) :- api_ac(Api, Orig, Protection), api_performs(Orig, Field, FOrig, remove, Gran, Id).
0.95 :: field_op_ac(Field, FOrig, Orig, index_exists, Gran, Protection) :- api_ac(Api, Orig, Protection), api_performs(Orig, Field, FOrig, index_exists, Gran, Id).
0.95 :: field_op_ac(Field, FOrig, Orig, value_exists, Gran, Protection) :- api_ac(Api, Orig, Protection), api_performs(Orig, Field, FOrig, value_exists, Gran, Id).
0.1 :: field_op_ac(Field, FOrig, Orig, fetch, Gran, Protection) :- api_ac(Api, Orig, Protection), api_performs(Orig, Field, FOrig, fetch, Gran, Id).
0.1 :: field_op_ac(Field, FOrig, Orig, modify, Gran, Protection) :- api_ac(Api, Orig, Protection), api_performs(Orig, Field, FOrig, modify, Gran, Id).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%Implication Rules
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Implication based on Parent-Child relationship
0.95 :: afield_op_ac(PField, Orig, Api, Op, Gran, Protection) :- parent_one_child(Field, PField, Id), field_op_ac(Field, Orig, Api, Op, Gran, Protection), \+field_op_ac(Field, Field, Api, Op, Gran, Protection).
0.95 :: afield_op_ac(PField, Orig, Api, Op, Gran, Protection) :- parent_n_child(Field, PField, Id), field_op_ac(Field, Orig, Api, Op, Gran, Protection), \+field_op_ac(Field, Field, Api, Op, Gran, Protection).
0.95 :: afield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- parent_one_child(Field, PField, Id), field_op_ac(PField, Orig, Api, Op, Gran, Protection), \+field_op_ac(Field, Field, Api, Op, Gran, Protection).
0.6 :: afield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- parent_n_child(Field, PField, Id), field_op_ac(PField, Orig, Api, Op, Gran, Protection), \+field_op_ac(Field, Field, Api, Op, Gran, Protection).

% Consolidate
bfield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- field_op_ac(Field, Orig, Api, Op, Gran, Protection).
bfield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- afield_op_ac(Field, Orig, Api, Op, Gran, Protection).

% Implication based on generic-specific relationship
0.95 :: cfield_op_ac(Field, Orig, Api, Op, all, Protection) :- bfield_op_ac(Field, Orig, Api, Op, some, Protection), \+bfield_op_ac(Field, Field, Api, Op, some, Protection).
0.4 :: cfield_op_ac(Field, Orig, Api, Op, some, Protection) :- bfield_op_ac(Field, Orig, Api, Op, all, Protection), \+bfield_op_ac(Field, Field, Api, Op, all, Protection).

% Consolidate
dfield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- bfield_op_ac(Field, Orig, Api, Op, Gran, Protection).
dfield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- cfield_op_ac(Field, Orig, Api, Op, Gran, Protection).

% Implication based on inter-operation relations
0.95 :: efield_op_ac(Field, Orig, Api, set, Gran, Protection) :- dfield_op_ac(Field, Orig, Api, get, Gran, Protection), \+dfield_op_ac(Field, Field, Api, get, Gran, Protection).
0.95 :: efield_op_ac(Field, Orig, Api, get, Gran, Protection) :- dfield_op_ac(Field, Orig, Api, value_exists, Gran, Protection), \+dfield_op_ac(Field, Field, Api, value_exists, Gran, Protection).
0.95 :: efield_op_ac(Field, Orig, Api, get, Gran, Protection) :- dfield_op_ac(Field, Orig, Api, index_exists, Gran, Protection), \+dfield_op_ac(Field, Field, Api, index_exists, Gran, Protection).
0.95 :: efield_op_ac(Field, Orig, Api, set, Gran, Protection) :- dfield_op_ac(Field, Orig, Api, add, Gran, Protection), \+dfield_op_ac(Field, Field, Api, add, Gran, Protection).

0.8 :: efield_op_ac(Field, Orig, Api, add, Gran, Protection) :- dfield_op_ac(Field, Orig, Api, remove, Gran, Protection), \+dfield_op_ac(Field, Field, Api, remove, Gran, Protection).
0.8 :: efield_op_ac(Field, Orig, Api, set, Gran, Protection) :- dfield_op_ac(Field, Orig, Api, add, Gran, Protection), \+dfield_op_ac(Field, Field, Api, add, Gran, Protection).

0.1 :: efield_op_ac(Field, Orig, Api, fetch, Gran, Protection) :- dfield_op_ac(Field, Orig, Api, get, Gran, Protection), \+dfield_op_ac(Field, Field, Api, get, Gran, Protection).
0.1 :: efield_op_ac(Field, Orig, Api, modify, Gran, Protection) :- dfield_op_ac(Field, Orig, Api, set, Gran, Protection), \+dfield_op_ac(Field, Field, Api, set, Gran, Protection).

% Consolidate
ffield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- dfield_op_ac(Field, Orig, Api, Op, Gran, Protection).
ffield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- efield_op_ac(Field, Orig, Api, Op, Gran, Protection).

% Implication based on sibling relations
P :: gfield_op_ac(SibField, Orig, Api, Op, Gran, Protection) :- similar_sibling(Field, SibField, Score, Id), ffield_op_ac(Field, Orig, Api, Op, Gran, Protection), \+ffield_op_ac(Field, Field, Api, Op, Gran, Protection), P is (0.6 * Score).
P :: gfield_op_ac(SibField, Orig, Api, Op, Gran, Protection) :- similar_sibling(SibField, Field, Score, Id), ffield_op_ac(Field, Orig, Api, Op, Gran, Protection), \+ffield_op_ac(Field, Field, Api, Op, Gran, Protection), P is (0.6 * Score).

% Consolidate
hfield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- ffield_op_ac(Field, Orig, Api, Op, Gran, Protection).
hfield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- gfield_op_ac(Field, Orig, Api, Op, Gran, Protection).

% Implication based on instance relations
0.6 :: ifield_op_ac(InsField, Orig, Api, Op, Gran, Protection) :- is_instance(InsField, Field, Id), hfield_op_ac(Field, Orig, Api, Op, Gran, Protection), \+hfield_op_ac(Field, Field, Api, Op, Gran, Protection).
0.6 :: ifield_op_ac(InsField, Orig, Api, Op, Gran, Protection) :- is_instance(Field, InsField, Id), hfield_op_ac(Field, Orig, Api, Op, Gran, Protection), \+hfield_op_ac(Field, Field, Api, Op, Gran, Protection).

% Consolidate
jfield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- hfield_op_ac(Field, Orig, Api, Op, Gran, Protection).
jfield_op_ac(Field, Orig, Api, Op, Gran, Protection) :- ifield_op_ac(Field, Orig, Api, Op, Gran, Protection).

% Final inference
fapi_ac(Api, Protection) :- api_performs(Api, Field, _, Op, Gran, Id), jfield_op_ac(Field, _, _, Op, Gran, Protection), \+jfield_op_ac(_, _, Api, _, _, _).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%Mock observations for each basic fact/implication constraint so the program does not fail with UnknownClause error
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

fw_ac(init, init, init, init).
parent_one_child(init, init, init).
parent_n_child(init, init, init).
similar_sibling(init, init, 0.6, init).
is_instance(init, init, init).
api_performs(init, init, init, init, init, init).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%Observations collected at runtime 
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%


