# moacswac
A web application consolidation software that uses multi-objective ant colony system

Requirements:
	- Java 7	

A software for cost-effective consolidation of multiple web applications in a cloud-based shared hosting environment. It uses multi-objective ant colony system (ACS) to build web application migration plans, which are then used to minimize over-provisioning of virtual machines (VMs) by consolidating web applications on under-utilized VMs. It optimizes two objectives that are ordered by their importance. The first objective is to maximize the number of released VMs. Moreover, since application migration is a resource-intensive operation, it also tries to minimize the number of application migrations.