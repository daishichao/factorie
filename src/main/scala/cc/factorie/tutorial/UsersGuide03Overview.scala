/*&

# Overview

At its heart FACTORIE is a toolkit for graphical models.  All its
specific applications, including their data representation, inference
and learning methods are built on a small set of common graphical
model primitives.

## Introduction to Graphical Models
  
Graphical models are a formalism in which a graph denotes the
conditional dependence structure between random variables.  The
formalism is the marriage between probability theory and graph theory.
It provides an elegant framework that combines uncertainty
(probabilities) and logical structure (independence constraints) such
that complex joint probability distributions over multiple variables
that would have otherwise been intractable to represent or manipulate
can instead be represented compactly and often manipulated
efficiently.  Since graphical models can straightforwardly express so
many different probabilistic models, they have become a lingua-franca
for statistics, machine learning, and data mining.

In graphical models, variables are depicted by the nodes a graph,
drawn as circles, and dependencies among variables are depicted by
edges, drawn either as directed (with arrows), or undirected (without
arrows).

There are two main types of graphical models.

*Directed graphical models* (also known as *Bayesian networks*)
represent a joint distribution over random variables by a product of
normalized conditional probability distributions (one for each "child"
variable, conditioned on the other "parent" variables that are
connected by an incoming directed edge).  They are convenient
generative models when variable values can be generated by an
ordered iteration of the graph nodes from "parent" variables to
"child" variables.

*Undirected graphical models* (also known as *Markov random fields*)
represent a joint distribution over random variables by a product of
unnormalized non-negative values (one value for each clique in the
graph).  They are convenient models for data in which it is not
intuitive to impose an ordering on the variables' generative process.
They can represent different patterns of independence constraints than
directed models can, and vice versa---neither one is strictly more
expressive than the other.

*Factor graphs* are a generalization of both directed and undirected
graphical models, capable of representing both.

When drawn, factor graphs depict variables as circular nodes in the
graph (just as in directed and undirected graphical models), however
rather than having edges that connect variables directly to each
other, edges instead connect variables to factors (which are drawn as
black squares).  In other words, variables are connected to other
variables only *through* factors.  The variables connected to a factor
are called the "neighbors" of the factor.

Factor graphs represent a joint distribution over random variables by
a product of (normalized or unnormalized) non-negative values---one
value for each factor in the graph.  The factors can be understood as
"compatibility functions" that take as input the values of the
variables which they neighbor, and outputing a "compatibility score"
such that higher scores indicate the combination of values is more
likely, and lower scores indicate the combination of values is less
likely.  A score of 0 indicates the combination is impossible.

Directed graphical models can be represented by having one factor for
each (child) variable, where the factor is also connected to the other
(parent) variables on whose values we must condition when generating
the child's value.  The factor's scores are normalized probabilities.

Undirected graphical models can be represented with one factor per
clique (or other arbitary subsets of clique members).  The factors'
scores are unnormalized non-negative values, and thus in order to
obtain a normalized joint probability distribution over all the
variables in the model, we must normalize the product of factor scores
by the proper normalization value (called the "partition function").

In practice, most implementations of graphical models use the log of
the score values so that they can be summed rather than multiplied,
and a larger dynamic range of scores can be represented with limited
floating-point precision in hardware.  Naturally these log-scores can
then vary from negative infinity (indicating an impossible combination
of values in the neighbors) to positive infinity (indicating an
obligatory combination of values in the neighbors).  Since the use of
log-scores is so common (and used in our implementation) in the
remainder of this documentation we will now simply use the term
"score" to refer to log-scores.


## Factor graphs in FACTORIE

The FACTORIE library defines Scala classes for representing the
elements of a factor graph.  Usually the names of our Scala classes
and methods are simply the standard names used in English
machine-learning or statistics vocabulary.

In this section we further describe factor graphs while introducing
FACTORIE class and method names indicated in `mono-space font`.  Class
(and trait) names are capitalized.  Method names begin with a
lowercase letter.

We provide here a high-level introduction to FACTORIE's identifiers.
Detailed explanations with code examples are given in subsequent chapters.

### FACTORIE modular design philosophy

FACTORIE is explicitly designed to support independent,
non-intertwined definitions of

1. data representations with variables,
2. models (value preferences, distributions, dependencies) with factors, 
3. inference methods, and
4. parameter estimation.

This separation provides FACTORIE users with great flexibilty to
mix-and-match different choices in each of these four dimensions.  For
example, the data representation for some task may be written just
once, while different choices of dependencies are explored for this
task---there is no need to re-write the data representation for each
experiment with a different model.  Similarly, inference methods may
be selected separately from the model definition.


### Representing data with variables and values

The data constituting any machine learning task are represented by
variables.  A variable can be understood as a container for a value.
A variable may also have certain relations to other variables (for
example, by being part of a sequence of variables corresponding to a
sequence of part-of-speech tags).  FACTORIE has many classes for
representing different types of variables.  The root of this class
hierarchy is the abstract trait `Var`.

An `Assignment` represents a mapping from variables to values, and can
be treated as a function.  The value of a variable `v1` in assignment
`a2` can be obtained by `a2(v1)`.

Note that an assignment stores a single value for a variable, not a
probability distribution over values.  (Of course FACTORIE has
extensive facilities for representing distributions over values, but
these are not intrinsic to a variable definition or
assignment---rather they are the purview of inference, because
different inference methods may choose to use different distribution
representations for the same variable.)

There are multiple concrete sub-classes of `Assignment`.  A
`MutableAssignment` can be modified.  A `TargetAssignment` returns the
gold-standard (e.g. human-labeler-provided) value for a
variable when such a labeled value is available.  An `Assignment1` is
a compact efficient representation for the value assignment of a
single variable (similarly `Assignment2..4` also exist).  A
`HashMapAssignment` is an arbitrarily-sized mutable assignment with
storage based on a HashMap.

Since we want to efficiently support the common case in which many
variables have relatively static value or unique assignments (because
they are observed, or because they are being sampled in a single MCMC
inference process) FACTORIE also has the `GlobalAssignment` object.
There may exist multiple instances of the other assignment classes,
each with its own values; however, there is only one
`GlobalAssignment`.  For efficiency the `GlobalAssignment` values are
not stored in the `GlobalAssignment`, but rather in the corresponding
variable instances themselves.

All variables have a `value` method that returns the variable's
currently assigned global value.  In other words for variable `v1`
`GlobalAssignment(v1)` is equivalent to `v1.value`.  Henceforth when
we refer to "a variable's value" we mean "a variable's
globally-assigned value".

All variables also have methods for testing the equality (`===`) or
inequality (`!==`) of their global values.  Thus `v1 === v2` will
return true if variables `v1` and `v2` have the same values, and is a
simple shorthand for `v1.value == v2.value`.  (Traditional equality
(with `==` or `equals`) on variables always tests the equality of the
system identity of the two variables, not their values.)

If some non-global assignment `a2` does not contain a value for `v1`
then looking up the value with `a2(v1)` may have different behavior in
different assignment subclasses.  For example, a `HashMapAssignment`
will throw an error, but an `Assignment1` (or `Assignment2`..`4`) will
simply return the variable's globally-assigned value.


#### Many types of variables

FACTORIE has different subclasses of `Var` for holding values of
different types.  There are a large number of traits and classes for
variables.  The following naming convensions make their interpretation
easier.  All abstract traits and classes end in `Var`, while concrete
classes end in `Variable`.  Almost all classes ending in `Variable`
have an abstract `Var` counterpart that does not necessarily specify
the mechanism by which the variable stores its value in memory.

The type of the value is named immediately before the `Var` or
`Variable` suffix (for example, `IntegerVariable` has values of type
Int).  Further modifiers may appear as prefixes.

All variables also have a member type `Value` indicating the Scala
type returned by its `value` method.  In some variables the `Value`
type is bound, but not assigned.  In other cases it is assigned.

Some variables have mutable value (inheriting from the trait
`MutableVar`).  Almost all classes ending in `Variable` are mutable.
Mutable variable values can be set with the `:=` method.  For example,
if `v1` has integer values, we can set the value of `v1` to 3 by `v1
:= 3`.


The following is a selection of FACTORIE's most widely-used variable classes.

`IntegerVariable`
: has value with Scala type Int.
`DoubleVariable`
: has value with Scala type Double.
`TensorVariable`
: has value of type Tensor, which is defined in the FACTORIE linear algebra package `cc.factorie.la`.  This variable class makes no restritions on the dimensionality of the tensor, nor the lengths of its dimensions.
`VectorVariable`
: has value of type Tensor1, which is a one-dimensional Tensor (also traditionally called a "vector").  In addition each `VectorVariable` is associated with a `DiscreteDomain` (further described below) whose size matches the length of the variable's vector value.
`DiscreteVariable extends VectorVar`
: has a value among N possible values, each of type `DiscreteValue`, and each associated with an integer 0 through N-1.  This `DiscreteValue` inherits from `Tensor1` and can also be interpreted as a "one-hot" vector with value 1.0 in one position and 0.0 everywhere else.  Given a `DiscreteValue dv1` its integer value can be obtained by `dv1.intValue`.  The length of the vector (in other words, the value of N) can be obtained by `dv1.length`.
`CategoricalVariable[A] extends DiscreteVar`
: has value among N possible values, each of type `CategoricalValue[A]` (which inherits from `DiscreteValue`), each associated with an integer 0 through N-1, and also associated with a "category" (often of type String).  These variables are often used for representing class labels and words, when a mapping from String category names to integer indices is desirable for efficiency (such as indexing into an array of parameters).  Given a `CategoricalValue[String] cv1` its integer value can be obtained by `cv1.intValue` and its categorical (String) value can be obtained by `cv1.categoryValue`.  Its value may be set with an integer: `cv1 := 2` or set by category string: `cv1 := "sports"`.  (The mapping between Strings and integers is stored in a `CategoricalDomain`, which is described below.)
`CategoricalVectorVariable[A] extends VectorVar`
: has value of type Tensor1, which is a one-dimensional Tensor.  In addition each `CategoricalVectorVariable` is associated with a `CategoricalDomain[A]`, which stores a mapping between values of type A (e.g. `String`) and integers.  Thus each position in the vector is associated with a category.  This variable type is useful for storing bag-of-words counts, for example.
`BooleanVariable extends CategoricalVar[Boolean]`
: has one of two possible values, each of type `BooleanValue` (which inherits from CategoricalValue[Boolean]), one of which is associated with integer 0 and boolean value false, the other of which is associated with integer value 1 and boolean value true.  Given a `BooleanValue bv1` its integer value can be obtained by `bv1.intValue` and its boolean value can be obtained by `bv1.booleanValue`.
`MassesVariable extends TensorVar`
: has value `Masses`, which are Tensors constrained to contain non-negative values.  `Masses` are useful as the parameters of Dirichlet distributions.
`ProportionsVariable extends MassesVar`
: has value `Proportions`, which are `Masses` constrained to sum to 1.0.  `Proportions` are useful as the parameters of discrete or multinomial distributions.
`RealVariable extends VectorVar`
: has a single real scalar value, stored in an object of type `RealValue` (which inherits from Tensor1).  This variable is similar to `DoubleValue` in that it stores a scalar value, however since its value type inherits from Tensor1, it can be used in dot products.

All of the above variable classes have constructors in which their
initial value may be set.  For example, `new IntegerVariable(3)` will
create a new variable whose initial value is 3.

Some of the above variable types have specializations for the case in
which human-labeled gold-standard target values are known.  These
specializations inherit from the `LabeledVar` trait which provides a
method `targetValue` that returns this gold-standard target value.
The target value is stored separately and may be different from the
variable's current global assignment value.  For example, if `i1` is a
`LabeledIntegerVariable` we can determine if the variable is currently
assigned to its gold-standard target value by evaluating the boolean
expression `i1.value == i1.targetValue`.  (The method `valueIsTarget`
is a convenient short-hand for this test.)  In almost all cases the
target value is initialized to be the value provided in the variable
constructor.

Common `LabeledVar` sub-classes include:

* LabeledDiscreteVariable
* LabeledCategoricalVariable[A]
* LabeledBooleanVariable
* LabeledStringVariable
* LabeledIntegerValue
* LabeledDoubleValue
* LabeledRealValue

All the above variable types are common in existing graphical models.
However, FACTORIE also has random variables for representing less
traditional values.  Although these may seem like peculiar value types
for a graphical model, they nontheless can be scored by a factor, and
are often useful in FACTORIE programs.

`StringVariable`
: has value with Scala type String.
`SeqVariable[A]`
: has value of type `Seq[A]`, that is, a sequence of objects of type `A`.
`ChainVariable[A] extends SeqVar[A]`
: has value of type `Seq[A]`, but the elements `A` must inherit from `ChainLink` which have `next` and `prev` methods.
`SpanVariable[A] extends SeqVar[A]`
: has value of type `Seq[A]`, and is a subsequence of a `ChainVar[A]`.
`SetVariable[A]`
: has value of type `Set[A]`, that is, an unordered set of objects of type `A`.
`RefVariable[A]`
: has value of type A.  In other words, it is a variable whose value is a pointer to a Scala object.
`EdgeVariable[A,B]`
: has value of type `Tuple[A,B]`, that is a pair of objects: a "source" of type `A` and a "destination" of type `B`.
`ArrowVariable[A,B] extends EdgeVar[A,B]
: like `EdgeVariable` has value of type `Tuple[A,B]`, but only the the "destination" is mutable, while the "source" is immutable.



#### Variable domains

Some (but not all) FACTORIE variables have a corresponding domain
object that contains information about the set of values the variable
may take on.  Such variables inherit from `VarWithDomain` which
provides a `domain` method that returns the variable's domain.

For example, `DiscreteVariable` subclasses have a corresponding
`DiscreteDomain` whose main role is to provide its `size`---the number
of possible values the variable can take on, i.e. from 0 to N-1.

`CategoricalVariable[A]` subclasses have a corresponding
`CategoricalDomain[A]` (inheriting from `DiscreteDomain`) which
provides not only its `size` but also a one-to-one mapping between the
categories and the integers 0 to N-1.  For example, if `cd1` is a
`CategoricalDomain[String]` then `cd1.category(3)` returns the String
corresponding at index in the domain.  `cd1.index("apple")` returns
the integer index corresponding to the cateogry value `"apple"`.  If
`"apple"` is not yet in the domain, it will be added, assigned the
next integer value, and the size of the domain will be increased by
one (assuming that `cd1` has not previously been frozen).


#### Undoable changes

FACTORIE also provides the ability to undo a collection of changes to
variable values.  (This is especially useful in Metropolis-Hastings
inference, in which a proposed change to variable values is made, but
may be rejected, requiring a reversion to previous values.)

A `Diff` instance represents a change in value to a single variable.
It has methods `undo` and `redo`, as well as the `variable` method for
getting the changed variable.  A `DiffList` stores an ordered list of
`Diff`s.

An alternative method for changing the value of a `MutableVar` is
`set`, which, in addition to the new value, also takes a `DiffList` as
an implicit argument.  The `set` method will then automatically
construct a `Diff` object and append it to the given `DiffList`.


### Expressing preferences with factors and models

A collection of variables and their values represents a possible state
of the world.  To express a degree of preference for one possible
world over another---or a probability distribution over possible
worlds---we need a mechanism for scoring the different worlds, such as
a collection of factors in a factor graph.

#### Factors

In FACTORIE, factors are represented by instances of the trait
`Factor`.  All `Factor`s have certain basic methods.  The
`numVariables` method returns the number of variables neighboring the
factor.  The list of neighboring variables themselves is returned by
the method `variables`.  Calling `currentScore` calculates the factor's
score for the neighboring variables' values in the current global
assignment.  You can obtain the score under alternative assignments by
calling `assignmentScore(a1)`, where `a1` is some `Assignment`.

Subclasses of the `Factor` trait include `Factor1`, `Factor2`,
`Factor3`, and `Factor4`, which have one, two, three or four
neighboring variables respectively.  (FACTORIE does not currently
support factors with more than four neighbors; if necessary these
could be added in the future, however, thus far we have not found them
necessary because (a) the use of `TensorVariable`, `SeqVariable` and
other composite-valued variables handle cases in which many values are
necessary, or (b) the use of a "var-args" `ContainerVariable` helps
similarly.)

The only abstract method in `Factor1` (and its other numbered
relations) is `score`, which takes as arguments the values of its
neighbors and returns a `Double`.

Some `Factor` subclasses define a `statistics` method that takes as
arguments the values of its neighbors and returns the sufficient
statistics of the factor.  (Often the sufficient statistics will
simply be the combination of neighbor values, but this method provides
users with an opportunity to instead perform various useful
transformations from values to sufficient statistics.)  In these cases
the `score` method is then defined to be the result of the
`statisticsScore` method, whose only argument is the statistics, thus
ensuring that the factor score can be calculated using only the
sufficient statistics.

For example, in the class `DotFactor2` the abstract method
`statistics` must return sufficient statistics of type `Tensor`.  The
`statisticsScore` method is then defined to be the dot-product of the
sufficient statistics with a `Tensor` of scoring parameters returned
by the abstract method `weights`.

The `DotFactorWithStatistics2` class inherits from `DotFactor2` but
requires that both its neighbors inhert frim `TensorVar`.  It then
defines its `statistics` method to return the outer-product of the
tensor values of its two neighbors.

The naming convension is that classes having suffix `WithStatistics`
define the `statistics` method for the user.  Most other classes have
an abstract `statistics` method that must be defined by the user.

#### Templated Factors

In many situations we want to model "relational data" in which the
variables and factors appear in repeated patterns of relations to each
other.

For example, in a hidden Markov model (HMM) there is a sequence of
observations (random variables with observed values) and a
corresponding sequence of hidden states (random variables whose value
is the identity of the state of a finite-state machine that was
responsible for generating that observation).  Each hidden state
variable has "next" and "previous" relations in the chain of hidden
states.  Each also has a corresponding observation variable (the one
that generated it, and occurs in the same sequence position).  

The standard HMM is "time invariant" (sometimes called "stationary"),
meaning that the hidden state-transition probabilities and the
observation-from-state generation probabilities do not dependent on
their position in the sequence.  Thus, although each transition needs
its own factor in the factor graph (because each factor has different
neighboring variables), each of these factors can share the same
scoring function.

In FACTORIE factors that share the same sufficient statistics
function, score function, and other similar attributes are said to
belong the same "family" of factors.  The trait `Family` defines a
`Factor` inner class such that its instances rely on methods in the
`Family` class for various shared functionality.

For example, the `DotFamily` provides a `weights` method for accessing
the dot-product parameters shared by all member factors.

In parallel to the numbered `Factor` subclasses there are numbered
families, `Family1`, `Family2`, `Family3`, `Family4`, each defining an
inner factor classes with the corresponding number of neighbors.

A `Template` is a subclass of `Family` that is also able to
automatically construct its factors on the fly in response to requests
to "unroll" part of the graphical model.  A `Template`'s `unroll`
method takes as input a variable, and uses its knowledge about the
"pattern of relational data" to create and return the `Template`'s
factors that neighbor the given variable.  

Note that for multi-neighbored factors this requires traversing some
relational pattern to find the *other* variables that are also
neighbors of these factors.  This knowledge is flexibly encoded in the
implementation of numbered "unroll" methods.  For example `Template2`
implements its `unroll` method in terms of two abstract methods:
`unroll1` and `unroll2`.  The `unroll1` method takes as input a
variable with type matching the first neighbor of the factor, and is
responsible creating and returning a (possibly empty) collection of
templated factors that touch this variable---finding the second
neighbor in each case by traversing some relational structure among
the variables.  

Implementing position-specific `unroll` methods may seem cumbersome,
but in most cases these method definitions are extremely succinct, and
moreover the ability to specify the relational pattern through
arbitrary Turing-complete code provides a great deal of flexibility.
Nonetheless, in the future, we plan to implement a simpler template
language on top of this unroll framework.

As with `Family` there are numerous specialized subclasses of
`Template`.  For example a `DotTemplateWithStatistics3` is a template
for a factor with three neighboring variables, each of which must
inherit from `TensorVar`; the template defines a factor scoring
function by the dot-product between the outer-product of the three
variables and the template's `weights`.  The only abstract methods are
`unroll1`, `unroll2`, `unroll3` and `weights`.

#### Models

For the sake of flexible modularity, in FACTORIE, a "model" does not
include the data, or inference method, or learning mechanism---a model
is purely a source of factors.  The key method of the `Model` class is
`factors` which takes as input a collection of variables, and returns
a collection of `Factor`s that neighbor those variables.  (This
definition allows efficient implementation of large relational models
in which factors are created on-the-fly in response to specific
requests instead of needing to be "unrolled" entirely in advance.)

The `Model` trait also provides alternative `factors` methods for
obtaining the factors that neighbor an individual variable, a `Diff`
or a `DiffList`.  There are also methods providing a filtered
collection of factors, such as `factorsOfClass`, `factorsOfFamily` or
`factorsOfFamilyClass`.  For example, the later may be used to obtain
only those factors that are members of the `DotFamily` class, and thus
conducive to gradient ascent parameter estimation.

`Model` also has convenience methods that return the total score of
all factors neighboring the method arguments.  For example, its
`assignmentScore` method returns the sum of the factors' scores
according to the given assignment.  Similarly `currentScore` does so
for the global assignment.

There are several concrete subclasses of `Model`.  A `TemplateModel`'s
factors come entirely from a collection of `Template`s.  By contrast,
an `ItemizedModel`

#### Parameters and Weights

The case in which templated factor scores are calculated by
dot-products is so common (and so relevant to our typical parameter
estimation procedures) that FACTORIE provides special support for this
case.

In a templated model the parameters are a set of weights tensors, one
per factor template.  The `Weights` trait is a `TensorVar` that holds
the parameters of one family.  For example, `DotFamily` has a
`weights` method that returns the `Weights` used to calculate its
factor scores.

A `WeightsSet` is then a collection of `Weights`, typically holding
all the parameters of a model based on dot-products.

To declare that an object has a `WeightsSet` holding parameters we use
the `Parameters` trait, which defines a `parameters` method returning
the `WeightsSet`.  (Thus this trait should not be thought of as
"being" parameters, but "holding" parameters.)  Its typical use-case
is `Model with Parameters`, declaring that the object is a source of
factors, and also has a `WeightsSet` available through its
`parameters` method.

During parameter estimation one typically needs additional tensors
having the same structure as the parameters, but that hold other
quantities, such as gradients or expectations.  For example the
`blankSparseMap` method on `WeightsSet` will create a "blank"
(initially zero-filled) `WeightsMap`, which is a collection of tensors
having the same structure as the `WeightsSet` that created it.  This
`WeightsMap` can then be filled with a gradient through some
computation on training data.  It is called a "map" because the
parameter-value-containing `Weights` objects are used as "keys" to
obtain the gradient tensor corresponding to that `Weights` object.

In summary, both `WeightsSet` and `WeightsMap` make available a
collection of `Tensor`s.  In `WeightsSet` the tensors are stored inside
the constituent `Weights` (`TensorVar`s) themselves.  In `WeightsMap`
the tensors are stored in a map internal to the `WeightsMap`; they are
accessible by lookup using the corresponding `Weights` as keys.

The abstract superclass of both `WeightsSet` and `WeightsMap` is
`TensorSet`.  Although a `TensorSet` is not a single tensor but a
collection of tensors, it has a variety of useful methods allowing it
to be treated somewhat like a single tensor.  These methods include
`oneNorm` and `twoNorm` for calculating norms, `+=` for incremental
addition, `dot` for the sum of dot-product of all constituent tensors,
and `different` to determine if the element-wise distance between two
`WeightsSet`s is larger than a threshold.



### Searching for solutions with inference



### Estimating parameters 




## Application packages

### Classification

### Regression

### Clustering

This package is not yet implemented, but will be in the future.

### Strings

### Natural language processing

### Topic Modeling

 */

// TODO We should explain why Family is needed rather than just defining a Factor subclass. -akm
