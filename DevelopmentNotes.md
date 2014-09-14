Development Notes
=================

These are some notes to understand the code.

#### The Execution Framework

zenboot seems to use the spring-events although the plugin does not seem to be installed. The Execution seem to be the only ApplicationEvent.
Such an event is generated in 4 possible ways:
* via the [ExecutionZoneController.groovy:33](https://github.com/hybris/zenboot/blob/master/grails-app/controllers/org/zenboot/portal/processing/ExecutionZoneController.groovy#L33)
* via the [ExposedExecutionZoneActionController.groovy:51](https://github.com/hybris/zenboot/blob/master/grails-app/controllers/org/zenboot/portal/processing/ExposedExecutionZoneActionController.groovy#L51) and
[ExposedExecutionZoneActionController.groovy:70](https://github.com/hybris/zenboot/blob/master/grails-app/controllers/org/zenboot/portal/processing/ExposedExecutionZoneActionController.groovy#L70)
* via the [CronjobService.groovy:27](https://github.com/hybris/zenboot/blob/master/grails-app/services/org/zenboot/portal/processing/CronjobService.groovy#L27)

Apart from the technical Details of the Event-system (ProcessingEvent), only the user and an [ExecutionZoneAction](https://github.com/hybris/zenboot/blob/master/grails-app/domain/org/zenboot/portal/processing/ExecutionZoneAction.groovy)
is passed as parameters. The ExecutionZoneAction is a domain-object, storing basically ONE ScriptletBatch which itself stores (at least) the scripts to execute.

In the first 4 cases the ExecutionZoneAction is created by the [ExecutionZoneService.groovy:81](https://github.com/hybris/zenboot/blob/345a46a1dc6059e33e778ab6ed93a2b7674c1192/grails-app/services/org/zenboot/portal/processing/ExecutionZoneService.groovy#L81)
(or another method for the exposed-variant) which basically calculates, based on the ExecutionZone, with some heavy help of [ControllerUtils](https://github.com/hybris/zenboot/blob/543e9f6e5882990b392e5cd4890d19d4424b82b2/src/groovy/org/zenboot/portal/ControllerUtils.groovy)
the ProcessingParameters (The ScriptletBatch is added later, see below).

The published ApplicationEvent is then captured by the [ScriptletBatchService:21](https://github.com/hybris/zenboot/blob/314a4e84df9689b71324690b1e81ea217d52f999/grails-app/services/org/zenboot/portal/processing/ScriptletBatchService.groovy#L21).
For Async-Support, we use the [executor-plugin](https://github.com/basejump/grails-executor) for async calls. Because it's configurable whether to use async, the method creates a closure.
Which get execute later in [ScriptletBatchService.groovy:43)](https://github.com/hybris/zenboot/blob/master/grails-app/services/org/zenboot/portal/processing/ScriptletBatchService.groovy#L43).

In the closure, the [ProcessContext](https://github.com/hybris/zenboot/blob/77d3fc58dd1bbbbb0073e41efe77d72422626353/src/groovy/org/zenboot/portal/processing/ProcessContext.groovy)
, a runtime-Object, is created and populated.
The ScriptletBatch is created with the help of [buildScriptletBatch](https://github.com/hybris/zenboot/blob/314a4e84df9689b71324690b1e81ea217d52f999/grails-app/services/org/zenboot/portal/processing/ScriptletBatchService.groovy#L94)
This method will also call addScriptlet which will [create the process-closure](https://github.com/hybris/zenboot/blob/314a4e84df9689b71324690b1e81ea217d52f999/grails-app/services/org/zenboot/portal/processing/ScriptletBatchService.groovy#L127)
which will later be called in the recursive-phase.

After that, the execution enters the recursion-phase by calling [ScriptletBatch.execute](https://github.com/hybris/zenboot/blob/master/grails-app/domain/org/zenboot/portal/processing/ScriptletBatch.groovy#L32) .

#### Abstract Processable and its two extensions: ScriptletBatch and Scriptlet

The Scriptlet represents a script as grails-Domain-Object. The ScripletBatch represents a whole batch of these scriptlets, also as domain-object.
The execution-logic is implemented in these two classes. We have:
* the execute-method ([Processable](https://github.com/hybris/zenboot/blob/master/grails-app/domain/org/zenboot/portal/processing/Processable.groovy#L33) /
[ScriptletBatch](https://github.com/hybris/zenboot/blob/master/grails-app/domain/org/zenboot/portal/processing/ScriptletBatch.groovy#L30) /
not overriden in Scriptlet)
* process-closures ( [ScriptletBatch](https://github.com/hybris/zenboot/blob/345a46a1dc6059e33e778ab6ed93a2b7674c1192/grails-app/domain/org/zenboot/portal/processing/ScriptletBatch.groovy#L36) /


The execute-method will deal with Exception-Handling and management of the "side-data" (storing Exceptions and logs, start- and stop-dates etc.) by calling [start/success/failure/stop-methods](https://github.com/hybris/zenboot/blob/master/grails-app/domain/org/zenboot/portal/processing/Processable.groovy#L85)
before/after it calls the process-method.

In the case of ScriptletBatch-process, it will call execute of all the processables in the list. The process closure of the ScriptLet has been
created in the buildScriptletBatch-phase before.

Because script-ececution is somewhere complicated on its own, let's have an own section on it, because here, ProcessHandlers come into place as well.

#### Execution of a Scriptlet



#### Quick-Hacks/-fixes
In Dev-environment, the [console-plugin](http://grails.org/plugin/console) is activated on http://localhost:8080/zenboot/console