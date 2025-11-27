package learnopengl_4_9_1

import learnopengl.shader.Shader
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

// settings
val SCR_WIDTH = 800
val SCR_HEIGHT = 600

@main def main(): Unit =

  // glfw: initialize and configure
  // ------------------------------
  if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW")
  GLFWErrorCallback.createPrint(System.err).set()
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
  glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
  glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

  if (System.getProperty("os.name").toLowerCase.contains("mac"))
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE)

  // glfw window creation
  // --------------------
  val window = glfwCreateWindow(SCR_WIDTH, SCR_HEIGHT, "LearnOpenGL", 0, 0)
  if (window == 0L) {
    glfwTerminate()
    throw new RuntimeException("Failed to create GLFW window")
  }
  glfwMakeContextCurrent(window)

  // load all OpenGL function pointers for the current context — it’s the LWJGL equivalent of gladLoadGLLoader
  GL.createCapabilities()

  // configure global opengl state
  // -----------------------------
  glEnable(GL_DEPTH_TEST)

  // build and compile our shader program
  // ------------------------------------
  val shader = Shader(
    "4.advanced_opengl/9.1.geometry_shader.vs",
    "4.advanced_opengl/9.1.geometry_shader.fs",
    "4.advanced_opengl/9.1.geometry_shader.gs"
  )

  // set up vertex data (and buffer(s)) and configure vertex attributes
  // ------------------------------------------------------------------
  val points: Array[Float] = Array(
    -0.5f, 0.5f, 1.0f, 0.0f, 0.0f, // top-left
    0.5f, 0.5f, 0.0f, 1.0f, 0.0f, // top-right
    0.5f, -0.5f, 0.0f, 0.0f, 1.0f, // bottom-right
    -0.5f, -0.5f, 1.0f, 1.0f, 0.0f // bottom-left
  )

  val VBO = glGenBuffers()
  val VAO = glGenVertexArrays()
  glBindVertexArray(VAO)
  glBindBuffer(GL_ARRAY_BUFFER, VBO)
  val pointsBuf = MemoryUtil.memAllocFloat(points.length)
  pointsBuf.put(points).flip()
  glBufferData(GL_ARRAY_BUFFER, pointsBuf, GL_STATIC_DRAW)
  MemoryUtil.memFree(pointsBuf)
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(
    0,
    2,
    GL_FLOAT,
    false,
    5 * java.lang.Float.BYTES,
    0L
  )
  glEnableVertexAttribArray(1)

  // You can unbind the VAO afterwards so other VAO calls won't accidentally modify this VAO, but this rarely happens. Modifying other
  // VAOs requires a call to glBindVertexArray anyways so we generally don't unbind VAOs (nor VBOs) when it's not directly necessary.
  // glBindVertexArray(0)

  glVertexAttribPointer(
    1,
    3,
    GL_FLOAT,
    false,
    5 * java.lang.Float.BYTES,
    2 * java.lang.Float.BYTES
  )
  glBindVertexArray(0)

  // render loop
  // -----------
  while (!glfwWindowShouldClose(window)) {
    // render
    // ------
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    // draw points
    shader.use()
    glBindVertexArray(VAO)
    glDrawArrays(GL_POINTS, 0, 4)

    // glfw: swap buffers and poll IO events (keys pressed/released, mouse moved etc.)
    // -------------------------------------------------------------------------------
    glfwSwapBuffers(window)
    glfwPollEvents()
  }

  // optional: de-allocate all resources once they've outlived their purpose:
  glDeleteVertexArrays(VAO)
  // ------------------------------------------------------------------------
  glDeleteBuffers(VBO)

  glfwTerminate()

// glfw: whenever the window size changed (by OS or user resize) this callback function executes
// ---------------------------------------------------------------------------------------------
def framebuffer_size_callback(window: Long, width: Int, height: Int): Unit = {
  // make sure the viewport matches the new window dimensions; note that width and
  // height will be significantly larger than specified on retina displays.
  glViewport(0, 0, width, height)
}
