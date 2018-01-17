package clients.security

import net.sourceforge.prograde.sm.ProGradeJSM
import java.security.Permission
import java.nio.file.Path
import java.io.FilePermission

class JShellSecurityManager extends ProGradeJSM() {

	val togglePerm = new ToggleSecurityManagerPermission()
	val threadLocal = new ThreadLocal[LocalSecurityModel]() {
		super.set(LocalSecurityModel(false, Nil))
		override def initialValue(): LocalSecurityModel = {
		  LocalSecurityModel(false, Nil)
		}
		override def set(value: LocalSecurityModel) {
			val securityManager = System.getSecurityManager();
			if (securityManager != null) {
				securityManager.checkPermission(togglePerm);
			}
			super.set(value);
		}
	}
	
	override def checkPermission(permission: Permission) {
		if (shouldCheck(permission)) {
		  if(permission.isInstanceOf[FilePermission]){
		    val fp = permission.asInstanceOf[FilePermission]
		    val found = this.threadLocal.get.allowPaths.find(path => {
		      fp.getName.startsWith(path.toAbsolutePath().toString())
		    }).isDefined
		    if(!found)
		      super.checkPermission(permission);
		  }else{
  		  super.checkPermission(permission);
		  }
		}
	}

	override def checkPermission(permission: Permission, context: Object) {
		if (shouldCheck(permission)) {
		  if(permission.isInstanceOf[FilePermission]){
		    val fp = permission.asInstanceOf[FilePermission]
		    val found = this.threadLocal.get.allowPaths.find(path => {
		      fp.getName.startsWith(path.toAbsolutePath().toString())
		    }).isDefined
		    if(!found)
		      super.checkPermission(permission);
		  }else{
  		  super.checkPermission(permission);
		  }
		}
	}

	def shouldCheck(permission: Permission): Boolean = {
			return isEnabled() || permission.isInstanceOf[ToggleSecurityManagerPermission];
	}

	def enable() {
		threadLocal.set(threadLocal.get.copy(enabled = true));
	}

	def disable() {
		threadLocal.set(threadLocal.get.copy(enabled = false));
	}

	def isEnabled(): Boolean = {
		threadLocal.get().enabled;
	}
	
	def setAllowPaths(list: List[Path]) {
	  threadLocal.set(threadLocal.get.copy(allowPaths = list))
	}
	
	def getAllowPaths(list: List[Path]) = {
    threadLocal.get.allowPaths
	}
}

case class LocalSecurityModel(enabled: Boolean, allowPaths: List[Path]) {
  
}

class ToggleSecurityManagerPermission extends Permission("ToggleSecurityManagerPermission") {
  val NAME = "ToggleSecurityManagerPermission"
  
  override def implies(permission: Permission): Boolean = {
    equals(permission)
  }

  override def equals(obj: Any): Boolean = {
    if (obj.isInstanceOf[ToggleSecurityManagerPermission]) {
      true
    }else
      false
  }

  override def hashCode(): Int = {
    NAME.hashCode()
  }

  override def getActions(): String = {
    ""
  }
}