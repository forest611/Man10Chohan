package red.man10.man10chohan

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.security.SecureRandom.getInstance
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.floor

class Man10Chohan : JavaPlugin() {


    companion object{

        const val prefix = "§f§l[§d§lMa§f§ln§a§l10§c§l丁§b§l半§f§l]"
        var game : Game? = null

        private val es = Executors.newSingleThreadExecutor()

        lateinit var vaultManager: VaultManager
        lateinit var mysql : MySQLManager


        fun format(double: Double):String{
            return String.format("%,.1f",double)
        }

        fun start(bet: Double){
            game = Game()
            game!!.bet = bet

            es.execute { game!!.betTimer() }

        }

        fun finish(){
            game = null
        }

        fun saveLog(p:Player,inMoney:Double,outMoney:Double){

            mysql.execute("INSERT INTO chohan_log " +
                    "(player, uuid, in_money, out_money, date) " +
                    "VALUES ('${p.name}', '${p.uniqueId}', ${inMoney}, ${outMoney}, DEFAULT)")

        }
    }

    private val minAmount = 10000.0
    private var isEnable = true

    override fun onEnable() {
        // Plugin startup logic

        saveDefaultConfig()

        vaultManager = VaultManager(this)
        mysql = MySQLManager(this,"ChohanLog")
        server.pluginManager.registerEvents(Game(),this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {

        if (sender !is Player)return true

        if (args.isNullOrEmpty()){

            if (!sender.hasPermission("chohan.user")){ return true }

            sender.sendMessage("${prefix}§e§l------------§0§l[§d§lMa§f§ln§a§l10§c§l丁§b§l半§0§l]§e§l------------")
            sender.sendMessage("${prefix}§c/mc open <bet>§f:丁半を開<金額は${minAmount}以上>")
            sender.sendMessage("${prefix}§c/mc c§f:§c丁(偶数)に賭ける")
            sender.sendMessage("${prefix}§c/mc h§f:§b半(奇数)に賭ける")
            if (game != null){
                sender.sendMessage("""
                    §e§l----------------------------------
                    §c丁:${game!!.playerCho.size}人
                    §b半:${game!!.playerHan.size}人
                    §e合計Bet:${format(game!!.getTotal())}円
                """.trimIndent())

            }


            return true
        }

        when(args[0]){

            "open" ->{

                if (!isEnable)return true

                if (!sender.hasPermission("chohan.user")){ return true }

                if (game != null){
                    sender.sendMessage("${prefix}§cすでにゲームが開いています！")
                    return true
                }

                if (args.size < 2){
                    sender.sendMessage("${prefix}§c入力方法に誤りがあります！")
                    return true
                }

                val bet = args[1].toDoubleOrNull()

                if (bet==null || bet<minAmount){
                    sender.sendMessage("${prefix}§6Bet金額に問題があります！")
                    return true
                }

                start(bet)
            }

            "c" ->{

                if (!sender.hasPermission("chohan.user")){ return true }

                if (game != null){
                    game!!.betCho(sender)
                    return true
                }
                sender.sendMessage("${prefix}§cゲームが開いていません！")

                return true
            }

            "h" ->{

                if (!sender.hasPermission("chohan.user")){ return true }

                if (game != null){
                    game!!.betHan(sender)
                    return true
                }
                sender.sendMessage("${prefix}§cゲームが開いていません！")

                return true
            }

            "on" ->{
                if (!sender.hasPermission("chohan.op")){ return true }

                isEnable = true
            }

            "off" ->{
                if (!sender.hasPermission("chohan.op")){ return true }

                isEnable = false
            }

        }


        return false
    }

    class Game : Listener{

        val playerCho = mutableListOf<UUID>()
        val playerHan = mutableListOf<UUID>()

        var bet: Double = 0.0
        var canJoin = true

        private val random = getInstance("NativePRNGNonBlocking")

        fun betCho(p:Player){

            if (!canJoin)return

            if (playerCho.contains(p.uniqueId)){
                p.sendMessage("${prefix}§c既に丁に賭けています！")
                return
            }

            if (playerHan.contains(p.uniqueId)){
                playerHan.remove(p.uniqueId)
                playerCho.add(p.uniqueId)
                p.sendMessage("${prefix}§c丁に移動しました！")
                return
            }

            if (vaultManager.withdraw(p.uniqueId,bet)){
                p.sendMessage("${prefix}§c丁に賭けました！")
                playerCho.add(p.uniqueId)
                return
            }

            p.sendMessage("${prefix}§c電子マネーが足りません！")

        }

        fun betHan(p:Player){

            if (!canJoin)return

            if (playerHan.contains(p.uniqueId)){
                p.sendMessage("${prefix}§b既に半に賭けています！")
                return
            }

            if (playerCho.contains(p.uniqueId)){
                playerCho.remove(p.uniqueId)
                playerHan.add(p.uniqueId)
                p.sendMessage("${prefix}§b半に移動しました！")
                return
            }

            if (vaultManager.withdraw(p.uniqueId,bet)){
                p.sendMessage("${prefix}§b半に賭けました！")
                playerHan.add(p.uniqueId)
                return
            }

            p.sendMessage("${prefix}§c電子マネーが足りません！")

        }

        fun quit(p:Player){
            playerHan.remove(p.uniqueId)
            playerCho.remove(p.uniqueId)
        }

        fun betTimer(){

            val textCho = Component.text("§c§l[丁(偶数)に賭ける]").clickEvent(ClickEvent.runCommand("/mc c"))
            val textHan = Component.text("§b§l[半(奇数)に賭ける]").clickEvent(ClickEvent.runCommand("/mc h"))

            Bukkit.broadcast(Component.text("${prefix}§6§l${format(bet)}円§a§l丁半が始められました!§f§l[/mc]"))
            Bukkit.broadcast(textCho.append(textHan))

            Thread.sleep(10000)

            for (i in 3 downTo 1){

                Bukkit.broadcast(Component.text("${prefix}§aBet受付終了まであと${i*10}秒"))

                Thread.sleep(10000)
            }

            if (playerCho.size+playerHan.size <= 1 ){
                Bukkit.broadcast(Component.text("${prefix}§4§l人数が集まらなかったため中止しました"))
                refund()
                finish()
                return
            }
            if (playerCho.isEmpty() || playerHan.isEmpty()){
                Bukkit.broadcast(Component.text("${prefix}§4§l人数が集まらなかったため中止しました"))
                refund()
                finish()
                return

            }

            canJoin = false

            sendGameUser("${prefix}§a§lサイコロを振っています…  §f§l§k123")

            Thread.sleep(3000)

            val result = random.nextInt(6) + 1

            val payout : Double

            val chouhan = if (result % 2 == 0){

                payout = floor(getTotal()/playerCho.size)

                es.execute {
                    for (p in playerCho){ win(Bukkit.getPlayer(p)?:continue,payout) }
                    for (p in playerHan){ lose(Bukkit.getPlayer(p)?:continue) }
                }

                "§c§l丁"
            }else{

                payout = floor(getTotal()/playerHan.size)

                es.execute {
                    for (p in playerHan){ win(Bukkit.getPlayer(p)?:continue,payout) }
                    for (p in playerCho){ lose(Bukkit.getPlayer(p)?:continue) }
                }

                "§b§l半"
            }

            sendGameUser("${prefix}§f結果: §e§l${result} ${chouhan}の勝利§c(支払額:${format(payout)}円 参加者:${playerCho.size+playerHan.size})")

            finish()

        }

        private fun win(p:Player,payout:Double){

            p.sendMessage("${prefix}§b§lあなたは勝ちました！")
            vaultManager.deposit(p.uniqueId,payout)
            Bukkit.getLogger().info("Chohan Win $payout ${p.name}")

            saveLog(p,bet,payout)

        }

        private fun lose(p:Player){
            p.sendMessage("${prefix}§c§lあなたは負けました！")

            saveLog(p,bet,0.0)
        }

        private fun refund(){

            for (p in playerHan){
                vaultManager.deposit(p,bet)
            }

            for (p in playerCho){
                vaultManager.deposit(p,bet)
            }

        }

        private fun sendGameUser(msg:String){
            for (p in playerHan){ (Bukkit.getPlayer(p)?:continue).sendMessage(msg) }
            for (p in playerCho){ (Bukkit.getPlayer(p)?:continue).sendMessage(msg) }

        }

        fun getTotal():Double{
            return (playerCho.size+playerHan.size)*bet
        }

        @EventHandler
        fun leaveEvent(e:PlayerQuitEvent){

            quit(e.player)

        }

    }



}